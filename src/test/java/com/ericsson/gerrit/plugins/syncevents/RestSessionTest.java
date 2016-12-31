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

package com.ericsson.gerrit.plugins.syncevents;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.gerrit.server.events.Event;
import com.google.gson.GsonBuilder;

import com.ericsson.gerrit.plugins.syncevents.SyncEventsResponseHandler.SyncResult;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class RestSessionTest {
  private static final String PLUGIN_NAME = "sync-events";
  private static final String REQUEST =
      Joiner.on("/").join("/plugins", PLUGIN_NAME, "event");

  private RestSession restSession;
  private Event event;

  @Before
  public void setup() {
    event = new SyncEventTest();
  }

  @Test
  public void testEventSentOK() throws Exception {
    setUpMocks(true, "", false);
    assertThat(restSession.send(event)).isTrue();
  }

  @Test
  public void testEventSentFailed() throws Exception {
    setUpMocks(false, "Error", false);
    assertThat(restSession.send(event)).isFalse();
  }

  @Test
  public void testEventSentThrowsException() throws Exception {
    setUpMocks(false, "Exception", true);
    assertThat(restSession.send(event)).isFalse();
  }

  private void setUpMocks(boolean isOk, String msg, boolean throwException)
      throws Exception {
    String content = new GsonBuilder().create().toJson(event);
    HttpSession httpSession = mock(HttpSession.class);
    if (throwException) {
      doThrow(new IOException()).when(httpSession).post(REQUEST, content);
    } else {
      SyncResult result = new SyncResult(isOk, msg);
      when(httpSession.post(REQUEST, content)).thenReturn(result);
    }
    restSession = new RestSession(httpSession, PLUGIN_NAME);
  }

  class SyncEventTest extends Event {
    public SyncEventTest() {
      super("test-event");
    }
  }
}
