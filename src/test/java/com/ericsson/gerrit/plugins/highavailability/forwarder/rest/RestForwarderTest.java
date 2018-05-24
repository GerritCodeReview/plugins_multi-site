// Copyright (C) 2015 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.google.common.base.Joiner;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.events.Event;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import javax.net.ssl.SSLException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;

public class RestForwarderTest {
  private static final String PLUGIN_NAME = "high-availability";
  private static final String EMPTY_MSG = "";
  private static final String ERROR = "Error";
  private static final String PLUGINS = "/plugins";
  private static final String PROJECT_TO_ADD = "projectToAdd";
  private static final String PROJECT_TO_DELETE = "projectToDelete";
  private static final String SUCCESS = "Success";
  private static final boolean SUCCESSFUL = true;
  private static final boolean FAILED = false;

  // Index
  private static final int CHANGE_NUMBER = 1;
  private static final String PROJECT_NAME = "test/project";
  private static final String PROJECT_NAME_URL_END = "test%2Fproject";
  private static final String INDEX_CHANGE_ENDPOINT =
      Joiner.on("/")
          .join(PLUGINS, PLUGIN_NAME, "index/change", PROJECT_NAME_URL_END + "~" + CHANGE_NUMBER);
  private static final String DELETE_CHANGE_ENDPOINT =
      Joiner.on("/").join("/plugins", PLUGIN_NAME, "index/change", "~" + CHANGE_NUMBER);
  private static final int ACCOUNT_NUMBER = 2;
  private static final String INDEX_ACCOUNT_ENDPOINT =
      Joiner.on("/").join(PLUGINS, PLUGIN_NAME, "index/account", ACCOUNT_NUMBER);
  private static final String UUID = "we235jdf92nfj2351";
  private static final String INDEX_GROUP_ENDPOINT =
      Joiner.on("/").join(PLUGINS, PLUGIN_NAME, "index/group", UUID);

  // Event
  private static final String EVENT_ENDPOINT = Joiner.on("/").join(PLUGINS, PLUGIN_NAME, "event");
  private static Event event = new Event("test-event") {};

  private RestForwarder forwarder;
  private HttpSession httpSessionMock;

  @Before
  public void setUp() {
    httpSessionMock = mock(HttpSession.class);
    Configuration configMock = mock(Configuration.class, Answers.RETURNS_DEEP_STUBS);
    when(configMock.http().maxTries()).thenReturn(3);
    when(configMock.http().retryInterval()).thenReturn(10);
    forwarder = new RestForwarder(httpSessionMock, PLUGIN_NAME, configMock);
  }

  @Test
  public void testIndexAccountOK() throws Exception {
    when(httpSessionMock.post(eq(INDEX_ACCOUNT_ENDPOINT), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.indexAccount(ACCOUNT_NUMBER, new IndexEvent())).isTrue();
  }

  @Test
  public void testIndexAccountFailed() throws Exception {
    when(httpSessionMock.post(eq(INDEX_ACCOUNT_ENDPOINT), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.indexAccount(ACCOUNT_NUMBER, new IndexEvent())).isFalse();
  }

  @Test
  public void testIndexAccountThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).post(eq(INDEX_ACCOUNT_ENDPOINT), any());
    assertThat(forwarder.indexAccount(ACCOUNT_NUMBER, new IndexEvent())).isFalse();
  }

  @Test
  public void testIndexGroupOK() throws Exception {
    when(httpSessionMock.post(eq(INDEX_GROUP_ENDPOINT), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.indexGroup(UUID, new IndexEvent())).isTrue();
  }

  @Test
  public void testIndexGroupFailed() throws Exception {
    when(httpSessionMock.post(eq(INDEX_GROUP_ENDPOINT), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.indexGroup(UUID, new IndexEvent())).isFalse();
  }

  @Test
  public void testIndexGroupThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).post(eq(INDEX_GROUP_ENDPOINT), any());
    assertThat(forwarder.indexGroup(UUID, new IndexEvent())).isFalse();
  }

  @Test
  public void testIndexChangeOK() throws Exception {
    when(httpSessionMock.post(eq(INDEX_CHANGE_ENDPOINT), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.indexChange(PROJECT_NAME, CHANGE_NUMBER, new IndexEvent())).isTrue();
  }

  @Test
  public void testIndexChangeFailed() throws Exception {
    when(httpSessionMock.post(eq(INDEX_CHANGE_ENDPOINT), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.indexChange(PROJECT_NAME, CHANGE_NUMBER, new IndexEvent())).isFalse();
  }

  @Test
  public void testIndexChangeThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).post(eq(INDEX_CHANGE_ENDPOINT), any());
    assertThat(forwarder.indexChange(PROJECT_NAME, CHANGE_NUMBER, new IndexEvent())).isFalse();
  }

  @Test
  public void testChangeDeletedFromIndexOK() throws Exception {
    when(httpSessionMock.delete(eq(DELETE_CHANGE_ENDPOINT), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.deleteChangeFromIndex(CHANGE_NUMBER, new IndexEvent())).isTrue();
  }

  @Test
  public void testChangeDeletedFromIndexFailed() throws Exception {
    when(httpSessionMock.delete(eq(DELETE_CHANGE_ENDPOINT), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.deleteChangeFromIndex(CHANGE_NUMBER, new IndexEvent())).isFalse();
  }

  @Test
  public void testChangeDeletedFromThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).delete(eq(DELETE_CHANGE_ENDPOINT), any());
    assertThat(forwarder.deleteChangeFromIndex(CHANGE_NUMBER, new IndexEvent())).isFalse();
  }

  @Test
  public void testEventSentOK() throws Exception {
    when(httpSessionMock.post(EVENT_ENDPOINT, event))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.send(event)).isTrue();
  }

  @Test
  public void testEventSentFailed() throws Exception {
    when(httpSessionMock.post(EVENT_ENDPOINT, event)).thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.send(event)).isFalse();
  }

  @Test
  public void testEventSentThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).post(EVENT_ENDPOINT, event);
    assertThat(forwarder.send(event)).isFalse();
  }

  @Test
  public void testEvictProjectOK() throws Exception {
    String key = PROJECT_NAME;
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.PROJECTS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.PROJECTS, key)).isTrue();
  }

  @Test
  public void testEvictAccountsOK() throws Exception {
    Account.Id key = new Account.Id(123);
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.ACCOUNTS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.ACCOUNTS, key)).isTrue();
  }

  @Test
  public void testEvictGroupsOK() throws Exception {
    AccountGroup.Id key = new AccountGroup.Id(123);
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.GROUPS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.GROUPS, key)).isTrue();
  }

  @Test
  public void testEvictGroupsByIncludeOK() throws Exception {
    AccountGroup.UUID key = new AccountGroup.UUID("90b3042d9094a37985f3f9281391dbbe9a5addad");
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.GROUPS_BYINCLUDE), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.GROUPS_BYINCLUDE, key)).isTrue();
  }

  @Test
  public void testEvictGroupsMembersOK() throws Exception {
    AccountGroup.UUID key = new AccountGroup.UUID("90b3042d9094a37985f3f9281391dbbe9a5addad");
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.GROUPS_MEMBERS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.GROUPS_MEMBERS, key)).isTrue();
  }

  @Test
  public void testEvictCacheFailed() throws Exception {
    String key = PROJECT_NAME;
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.PROJECTS), keyJson))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.PROJECTS, key)).isFalse();
  }

  @Test
  public void testEvictCacheThrowsException() throws Exception {
    String key = PROJECT_NAME;
    String keyJson = new GsonBuilder().create().toJson(key);
    doThrow(new IOException())
        .when(httpSessionMock)
        .post(buildCacheEndpoint(Constants.PROJECTS), keyJson);
    assertThat(forwarder.evict(Constants.PROJECTS, key)).isFalse();
  }

  private static String buildCacheEndpoint(String name) {
    return Joiner.on("/").join(PLUGINS, PLUGIN_NAME, "cache", name);
  }

  @Test
  public void testAddToProjectListOK() throws Exception {
    String projectName = PROJECT_TO_ADD;
    when(httpSessionMock.post(buildProjectListCacheEndpoint(projectName)))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.addToProjectList(projectName)).isTrue();
  }

  @Test
  public void testAddToProjectListFailed() throws Exception {
    String projectName = PROJECT_TO_ADD;
    when(httpSessionMock.post(buildProjectListCacheEndpoint(projectName)))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.addToProjectList(projectName)).isFalse();
  }

  @Test
  public void testAddToProjectListThrowsException() throws Exception {
    String projectName = PROJECT_TO_ADD;
    doThrow(new IOException())
        .when(httpSessionMock)
        .post(buildProjectListCacheEndpoint(projectName));
    assertThat(forwarder.addToProjectList(projectName)).isFalse();
  }

  @Test
  public void testRemoveFromProjectListOK() throws Exception {
    String projectName = PROJECT_TO_DELETE;
    when(httpSessionMock.delete(buildProjectListCacheEndpoint(projectName)))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.removeFromProjectList(projectName)).isTrue();
  }

  @Test
  public void testRemoveToProjectListFailed() throws Exception {
    String projectName = PROJECT_TO_DELETE;
    when(httpSessionMock.delete(buildProjectListCacheEndpoint(projectName)))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.removeFromProjectList(projectName)).isFalse();
  }

  @Test
  public void testRemoveToProjectListThrowsException() throws Exception {
    String projectName = PROJECT_TO_DELETE;
    doThrow(new IOException())
        .when(httpSessionMock)
        .delete((buildProjectListCacheEndpoint(projectName)));
    assertThat(forwarder.removeFromProjectList(projectName)).isFalse();
  }

  private static String buildProjectListCacheEndpoint(String projectName) {
    return Joiner.on("/").join(buildCacheEndpoint(Constants.PROJECT_LIST), projectName);
  }

  @Test
  public void testRetryOnErrorThenSuccess() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(true, SUCCESS));

    assertThat(forwarder.evict(Constants.PROJECT_LIST, new Object())).isTrue();
  }

  @Test
  public void testRetryOnIoExceptionThenSuccess() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenThrow(new IOException())
        .thenThrow(new IOException())
        .thenReturn(new HttpResult(true, SUCCESS));

    assertThat(forwarder.evict(Constants.PROJECT_LIST, new Object())).isTrue();
  }

  @Test
  public void testNoRetryAfterNonRecoverableException() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenThrow(new SSLException("Non Recoverable"))
        .thenReturn(new HttpResult(true, SUCCESS));

    assertThat(forwarder.evict(Constants.PROJECT_LIST, new Object())).isFalse();
  }

  @Test
  public void testFailureAfterMaxTries() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(false, ERROR))
        .thenReturn(new HttpResult(false, ERROR));

    assertThat(forwarder.evict(Constants.PROJECT_LIST, new Object())).isFalse();
  }
}
