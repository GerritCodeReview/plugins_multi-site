// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.multisite.forwarder.rest;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.rest.HttpResponseHandler.HttpResult;
import com.googlesource.gerrit.plugins.multisite.peers.PeerInfo;
import java.io.IOException;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;

public class RestIndexEventForwarderTest {
  private static final String URL = "http://fake.com";
  private static final String PLUGIN_NAME = "multi-site";
  private static final String EMPTY_MSG = "";
  private static final String PLUGINS = "plugins";
  private static final boolean SUCCESSFUL = true;
  private static final boolean FAILED = false;

  // Index
  private static final int CHANGE_NUMBER = 1;
  private static final String PROJECT_NAME = "test/project";
  private static final String PROJECT_NAME_URL_END = "test%2Fproject";
  private static final String INDEX_CHANGE_ENDPOINT =
      Joiner.on("/")
          .join(
              URL,
              PLUGINS,
              PLUGIN_NAME,
              "index/change",
              PROJECT_NAME_URL_END + "~" + CHANGE_NUMBER);
  private static final String DELETE_CHANGE_ENDPOINT =
      Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "index/change", "~" + CHANGE_NUMBER);
  private static final int ACCOUNT_NUMBER = 2;
  private static final String INDEX_ACCOUNT_ENDPOINT =
      Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "index/account", ACCOUNT_NUMBER);
  private static final String UUID = "we235jdf92nfj2351";
  private static final String INDEX_GROUP_ENDPOINT =
      Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "index/group", UUID);

  private IndexEventForwarder indexForwarder;
  private HttpSession httpSessionMock;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    httpSessionMock = mock(HttpSession.class);
    Configuration configMock = mock(Configuration.class, Answers.RETURNS_DEEP_STUBS);
    when(configMock.http().maxTries()).thenReturn(3);
    when(configMock.http().retryInterval()).thenReturn(10);
    Provider<Set<PeerInfo>> peersMock = mock(Provider.class);
    when(peersMock.get()).thenReturn(ImmutableSet.of(new PeerInfo(URL)));
    indexForwarder =
        new RestIndexEventForwarder(
            httpSessionMock, PLUGIN_NAME, configMock, peersMock); // TODO: Create provider
  }

  @Test
  public void testIndexAccountOK() throws Exception {
    when(httpSessionMock.post(eq(INDEX_ACCOUNT_ENDPOINT), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(indexForwarder.indexAccount(new AccountIndexEvent(ACCOUNT_NUMBER))).isTrue();
  }

  @Test
  public void testIndexAccountFailed() throws Exception {
    when(httpSessionMock.post(eq(INDEX_ACCOUNT_ENDPOINT), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(indexForwarder.indexAccount(new AccountIndexEvent(ACCOUNT_NUMBER))).isFalse();
  }

  @Test
  public void testIndexAccountThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).post(eq(INDEX_ACCOUNT_ENDPOINT), any());
    assertThat(indexForwarder.indexAccount(new AccountIndexEvent(ACCOUNT_NUMBER))).isFalse();
  }

  @Test
  public void testIndexGroupOK() throws Exception {
    when(httpSessionMock.post(eq(INDEX_GROUP_ENDPOINT), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(indexForwarder.indexGroup(new GroupIndexEvent(UUID))).isTrue();
  }

  @Test
  public void testIndexGroupFailed() throws Exception {
    when(httpSessionMock.post(eq(INDEX_GROUP_ENDPOINT), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(indexForwarder.indexGroup(new GroupIndexEvent(UUID))).isFalse();
  }

  @Test
  public void testIndexGroupThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).post(eq(INDEX_GROUP_ENDPOINT), any());
    assertThat(indexForwarder.indexGroup(new GroupIndexEvent(UUID))).isFalse();
  }

  @Test
  public void testIndexChangeOK() throws Exception {
    when(httpSessionMock.post(eq(INDEX_CHANGE_ENDPOINT), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(indexForwarder.indexChange(new ChangeIndexEvent(PROJECT_NAME, CHANGE_NUMBER, false)))
        .isTrue();
  }

  @Test
  public void testIndexChangeFailed() throws Exception {
    when(httpSessionMock.post(eq(INDEX_CHANGE_ENDPOINT), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(indexForwarder.indexChange(new ChangeIndexEvent(PROJECT_NAME, CHANGE_NUMBER, false)))
        .isFalse();
  }

  @Test
  public void testIndexChangeThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).post(eq(INDEX_CHANGE_ENDPOINT), any());
    assertThat(indexForwarder.indexChange(new ChangeIndexEvent(PROJECT_NAME, CHANGE_NUMBER, false)))
        .isFalse();
  }

  @Test
  public void testChangeDeletedFromIndexOK() throws Exception {
    when(httpSessionMock.delete(eq(DELETE_CHANGE_ENDPOINT), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(
            indexForwarder.deleteChangeFromIndex(
                new ChangeIndexEvent(PROJECT_NAME, CHANGE_NUMBER, true)))
        .isTrue();
  }

  @Test
  public void testChangeDeletedFromIndexFailed() throws Exception {
    when(httpSessionMock.delete(eq(DELETE_CHANGE_ENDPOINT), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(
            indexForwarder.deleteChangeFromIndex(
                new ChangeIndexEvent(PROJECT_NAME, CHANGE_NUMBER, false)))
        .isFalse();
  }

  @Test
  public void testChangeDeletedFromThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).delete(eq(DELETE_CHANGE_ENDPOINT), any());
    assertThat(
            indexForwarder.deleteChangeFromIndex(
                new ChangeIndexEvent(PROJECT_NAME, CHANGE_NUMBER, false)))
        .isFalse();
  }
}
