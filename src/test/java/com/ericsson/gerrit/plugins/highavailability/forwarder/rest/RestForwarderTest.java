// Copyright (C) 2015 Ericsson
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.google.common.base.Joiner;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.events.Event;
import com.google.gson.GsonBuilder;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import java.io.IOException;
import javax.net.ssl.SSLException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RestForwarderTest {
  private static final String PLUGIN_NAME = "high-availability";
  private static final String EMPTY_MSG = "";
  private static final boolean SUCCESSFUL = true;
  private static final boolean FAILED = false;

  //Index
  private static final int CHANGE_NUMBER = 1;
  private static final String INDEX_CHANGE_ENDPOINT =
      Joiner.on("/").join("/plugins", PLUGIN_NAME, "index/change", CHANGE_NUMBER);
  private static final int ACCOUNT_NUMBER = 2;
  private static final String INDEX_ACCOUNT_ENDPOINT =
      Joiner.on("/").join("/plugins", PLUGIN_NAME, "index/account", ACCOUNT_NUMBER);

  //Event
  private static final String EVENT_ENDPOINT =
      Joiner.on("/").join("/plugins", PLUGIN_NAME, "event");
  private static Event event = new Event("test-event") {};
  private static String eventJson = new GsonBuilder().create().toJson(event);

  private RestForwarder forwarder;
  private HttpSession httpSessionMock;
  private Configuration configurationMock;

  @BeforeClass
  public static void setup() {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  @Before
  public void setUp() {
    httpSessionMock = mock(HttpSession.class);
    configurationMock = mock(Configuration.class);
    when(configurationMock.getMaxTries()).thenReturn(3);
    when(configurationMock.getRetryInterval()).thenReturn(10);
    forwarder = new RestForwarder(httpSessionMock, PLUGIN_NAME, configurationMock);
  }

  @Test
  public void testIndexAccountOK() throws Exception {
    when(httpSessionMock.post(INDEX_ACCOUNT_ENDPOINT))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.indexAccount(ACCOUNT_NUMBER)).isTrue();
  }

  @Test
  public void testIndexAccountFailed() throws Exception {
    when(httpSessionMock.post(INDEX_ACCOUNT_ENDPOINT))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.indexAccount(ACCOUNT_NUMBER)).isFalse();
  }

  @Test
  public void testIndexAccountThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).post(INDEX_ACCOUNT_ENDPOINT);
    assertThat(forwarder.indexAccount(ACCOUNT_NUMBER)).isFalse();
  }

  @Test
  public void testIndexChangeOK() throws Exception {
    when(httpSessionMock.post(INDEX_CHANGE_ENDPOINT))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.indexChange(CHANGE_NUMBER)).isTrue();
  }

  @Test
  public void testIndexChangeFailed() throws Exception {
    when(httpSessionMock.post(INDEX_CHANGE_ENDPOINT)).thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.indexChange(CHANGE_NUMBER)).isFalse();
  }

  @Test
  public void testIndexChangeThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).post(INDEX_CHANGE_ENDPOINT);
    assertThat(forwarder.indexChange(CHANGE_NUMBER)).isFalse();
  }

  @Test
  public void testChangeDeletedFromIndexOK() throws Exception {
    when(httpSessionMock.delete(INDEX_CHANGE_ENDPOINT))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.deleteChangeFromIndex(CHANGE_NUMBER)).isTrue();
  }

  @Test
  public void testChangeDeletedFromIndexFailed() throws Exception {
    when(httpSessionMock.delete(INDEX_CHANGE_ENDPOINT))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.deleteChangeFromIndex(CHANGE_NUMBER)).isFalse();
  }

  @Test
  public void testChangeDeletedFromThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).delete(INDEX_CHANGE_ENDPOINT);
    assertThat(forwarder.deleteChangeFromIndex(CHANGE_NUMBER)).isFalse();
  }

  @Test
  public void testEventSentOK() throws Exception {
    when(httpSessionMock.post(EVENT_ENDPOINT, eventJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.send(event)).isTrue();
  }

  @Test
  public void testEventSentFailed() throws Exception {
    when(httpSessionMock.post(EVENT_ENDPOINT, eventJson))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.send(event)).isFalse();
  }

  @Test
  public void testEventSentThrowsException() throws Exception {
    doThrow(new IOException()).when(httpSessionMock).post(EVENT_ENDPOINT, eventJson);
    assertThat(forwarder.send(event)).isFalse();
  }

  @Test
  public void testEvictProjectOK() throws Exception {
    String key = "projectName";
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.PROJECTS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.PROJECTS, key)).isTrue();
  }

  @Test
  public void testEvictAccountsOK() throws Exception {
    Account.Id key = Account.Id.parse("123");
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.ACCOUNTS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.ACCOUNTS, key)).isTrue();
  }

  @Test
  public void testEvictGroupsOK() throws Exception {
    AccountGroup.Id key = AccountGroup.Id.parse("123");
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.GROUPS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.GROUPS, key)).isTrue();
  }

  @Test
  public void testEvictGroupsByIncludeOK() throws Exception {
    AccountGroup.UUID key = AccountGroup.UUID.parse("90b3042d9094a37985f3f9281391dbbe9a5addad");
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.GROUPS_BYINCLUDE), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.GROUPS_BYINCLUDE, key)).isTrue();
  }

  @Test
  public void testEvictGroupsMembersOK() throws Exception {
    AccountGroup.UUID key = AccountGroup.UUID.parse("90b3042d9094a37985f3f9281391dbbe9a5addad");
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.GROUPS_MEMBERS), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.GROUPS_MEMBERS, key)).isTrue();
  }

  @Test
  public void testEvictProjectListOK() throws Exception {
    String key = "all";
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.PROJECT_LIST), keyJson))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.PROJECT_LIST, key)).isTrue();
  }

  @Test
  public void testEvictCacheFailed() throws Exception {
    String key = "projectName";
    String keyJson = new GsonBuilder().create().toJson(key);
    when(httpSessionMock.post(buildCacheEndpoint(Constants.PROJECTS), keyJson))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.evict(Constants.PROJECTS, key)).isFalse();
  }

  @Test
  public void testEvictCacheThrowsException() throws Exception {
    String key = "projectName";
    String keyJson = new GsonBuilder().create().toJson(key);
    doThrow(new IOException())
        .when(httpSessionMock)
        .post(buildCacheEndpoint(Constants.PROJECTS), keyJson);
    assertThat(forwarder.evict(Constants.PROJECTS, key)).isFalse();
  }

  private String buildCacheEndpoint(String name) {
    return Joiner.on("/").join("/plugins", PLUGIN_NAME, "cache", name);
  }

  @Test
  public void testRetryOnErrorThenSuccess() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenReturn(new HttpResult(false, "Error"))
        .thenReturn(new HttpResult(false, "Error"))
        .thenReturn(new HttpResult(true, "Success"));

    assertThat(forwarder.evict(Constants.PROJECT_LIST, new Object())).isTrue();
  }

  @Test
  public void testRetryOnIoExceptionThenSuccess() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenThrow(new IOException())
        .thenThrow(new IOException())
        .thenReturn(new HttpResult(true, "Success"));

    assertThat(forwarder.evict(Constants.PROJECT_LIST, new Object())).isTrue();
  }

  @Test
  public void testNoRetryAfterNonRecoverableException() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenThrow(new SSLException("Non Recoverable"))
        .thenReturn(new HttpResult(true, "Success"));

    assertThat(forwarder.evict(Constants.PROJECT_LIST, new Object())).isFalse();
  }

  @Test
  public void testFailureAfterMaxTries() throws IOException {
    when(httpSessionMock.post(anyString(), anyString()))
        .thenReturn(new HttpResult(false, "Error"))
        .thenReturn(new HttpResult(false, "Error"))
        .thenReturn(new HttpResult(false, "Error"));

    assertThat(forwarder.evict(Constants.PROJECT_LIST, new Object())).isFalse();
  }
}
