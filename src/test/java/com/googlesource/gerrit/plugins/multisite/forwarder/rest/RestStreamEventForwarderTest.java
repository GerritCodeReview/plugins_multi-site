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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.events.Event;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.StreamEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.rest.HttpResponseHandler.HttpResult;
import com.googlesource.gerrit.plugins.multisite.peers.PeerInfo;
import java.io.IOException;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;

public class RestStreamEventForwarderTest {
  private static final String URL = "http://fake.com";
  private static final String PLUGIN_NAME = "multi-site";
  private static final String EMPTY_MSG = "";
  private static final String PLUGINS = "plugins";
  private static final boolean SUCCESSFUL = true;
  private static final boolean FAILED = false;

  // Event
  private static Event event = new Event("test-event") {};
  private static final String EVENT_ENDPOINT =
      Joiner.on("/").join(URL, PLUGINS, PLUGIN_NAME, "event", event.type);

  private StreamEventForwarder forwarder;
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
        new RestStreamEventForwarder(
            httpSessionMock, PLUGIN_NAME, configMock, peersMock); // TODO: Create provider
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
}
