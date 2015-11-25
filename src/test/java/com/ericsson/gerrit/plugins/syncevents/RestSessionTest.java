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
import static org.easymock.EasyMock.expect;

import com.google.common.base.Joiner;
import com.google.gerrit.server.events.Event;
import com.google.gson.GsonBuilder;

import com.ericsson.gerrit.plugins.syncevents.HttpSession;
import com.ericsson.gerrit.plugins.syncevents.RestSession;
import com.ericsson.gerrit.plugins.syncevents.SyncEventsResponseHandler.SyncResult;

import org.easymock.EasyMockSupport;
import org.junit.Test;

import java.io.IOException;

public class RestSessionTest extends EasyMockSupport {
  private static final String PLUGIN_NAME = "sync-events";

  private RestSession restClient;
  private Event event;

  @Test
  public void testEventSentOK() throws Exception {
    event = setUpMocks(true, "", false);
    assertThat(restClient.send(event)).isTrue();
  }

  @Test
  public void testEventSentFailed() throws Exception {
    event = setUpMocks(false, "Error", false);
    assertThat(restClient.send(event)).isFalse();
  }

  @Test
  public void testEventSentThrowsException() throws Exception {
    event = setUpMocks(false, "Exception", true);
    assertThat(restClient.send(event)).isFalse();
  }

  private Event setUpMocks(boolean ok, String msg, boolean exception)
      throws Exception {
    String request = Joiner.on("/").join("/plugins", PLUGIN_NAME, "event");
    SyncEventTest testEvent = new SyncEventTest();
    String content = new GsonBuilder().create().toJson(testEvent);
    HttpSession httpSession = createNiceMock(HttpSession.class);
    if (exception) {
      expect(httpSession.post(request, content)).andThrow(new IOException());
    } else {
      SyncResult result = new SyncResult(ok, msg);
      expect(httpSession.post(request, content)).andReturn(result);
    }
    restClient = new RestSession(httpSession, PLUGIN_NAME);
    replayAll();
    return testEvent;
  }

  class SyncEventTest extends Event {
    public SyncEventTest() {
      super("test-event");
    }
  }
}
