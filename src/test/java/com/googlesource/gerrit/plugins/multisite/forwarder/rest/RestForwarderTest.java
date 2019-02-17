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
import com.googlesource.gerrit.plugins.multisite.cache.Constants;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.rest.HttpResponseHandler.HttpResult;
import com.googlesource.gerrit.plugins.multisite.peers.PeerInfo;
import java.io.IOException;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;

public class RestForwarderTest {
  private static final String URL = "http://fake.com";
  private static final String PLUGIN_NAME = "multi-site";
  private static final String EMPTY_MSG = "";
  private static final String PLUGINS = "plugins";
  private static final String PROJECT_TO_ADD = "projectToAdd";
  private static final String PROJECT_TO_DELETE = "projectToDelete";
  private static final boolean SUCCESSFUL = true;
  private static final boolean FAILED = false;

  // Event
  private RestForwarder forwarder;
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
    forwarder =
        new RestForwarder(
            httpSessionMock, PLUGIN_NAME, configMock, peersMock); // TODO: Create provider
  }

  private static String buildCacheEndpoint(String name) {
    return Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "cache", name);
  }

  @Test
  public void testAddToProjectListOK() throws Exception {
    String projectName = PROJECT_TO_ADD;
    when(httpSessionMock.post(buildProjectListCacheEndpoint(projectName), null))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.updateProjectList(new ProjectListUpdateEvent(projectName, false)))
        .isTrue();
  }

  @Test
  public void testAddToProjectListFailed() throws Exception {
    String projectName = PROJECT_TO_ADD;
    when(httpSessionMock.post(buildProjectListCacheEndpoint(projectName), null))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.updateProjectList(new ProjectListUpdateEvent(projectName, false)))
        .isFalse();
  }

  @Test
  public void testAddToProjectListThrowsException() throws Exception {
    String projectName = PROJECT_TO_ADD;
    doThrow(new IOException())
        .when(httpSessionMock)
        .post(buildProjectListCacheEndpoint(projectName), null);
    assertThat(forwarder.updateProjectList(new ProjectListUpdateEvent(projectName, false)))
        .isFalse();
  }

  @Test
  public void testRemoveFromProjectListOK() throws Exception {
    String projectName = PROJECT_TO_DELETE;
    when(httpSessionMock.delete(eq(buildProjectListCacheEndpoint(projectName)), any()))
        .thenReturn(new HttpResult(SUCCESSFUL, EMPTY_MSG));
    assertThat(forwarder.updateProjectList(new ProjectListUpdateEvent(projectName, true))).isTrue();
  }

  @Test
  public void testRemoveToProjectListFailed() throws Exception {
    String projectName = PROJECT_TO_DELETE;
    when(httpSessionMock.delete(eq(buildProjectListCacheEndpoint(projectName)), any()))
        .thenReturn(new HttpResult(FAILED, EMPTY_MSG));
    assertThat(forwarder.updateProjectList(new ProjectListUpdateEvent(projectName, true)))
        .isFalse();
  }

  @Test
  public void testRemoveToProjectListThrowsException() throws Exception {
    String projectName = PROJECT_TO_DELETE;
    doThrow(new IOException())
        .when(httpSessionMock)
        .delete(eq(buildProjectListCacheEndpoint(projectName)), any());
    assertThat(forwarder.updateProjectList(new ProjectListUpdateEvent(projectName, true)))
        .isFalse();
  }

  private static String buildProjectListCacheEndpoint(String projectName) {
    return Joiner.on("/").join(buildCacheEndpoint(Constants.PROJECT_LIST), projectName);
  }
}
