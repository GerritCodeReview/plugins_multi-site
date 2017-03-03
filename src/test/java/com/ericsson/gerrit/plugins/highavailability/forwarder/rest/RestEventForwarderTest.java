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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Joiner;
import com.google.gerrit.server.events.Event;
import com.google.gson.GsonBuilder;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;

import org.junit.Test;

import java.io.IOException;

public class RestEventForwarderTest {
  private static final int CHANGE_NUMBER = 1;
  private static final String DELETE_OP = "delete";
  private static final String INDEX_OP = "index";
  private static final String PLUGIN_NAME = "multi-master";
  private static final String EMPTY_MSG = "";
  private static final String ERROR_MSG = "Error";
  private static final String EXCEPTION_MSG = "Exception";
  private static final boolean SUCCESSFUL = true;
  private static final boolean FAILED = false;
  private static final boolean DO_NOT_THROW_EXCEPTION = false;
  private static final boolean THROW_EXCEPTION = true;

  private RestEventForwarder eventForwarder;

  @Test
  public void testIndexChangeOK() throws Exception {
    setUpMocksForIndex(INDEX_OP, SUCCESSFUL, EMPTY_MSG, DO_NOT_THROW_EXCEPTION);
    assertThat(eventForwarder.indexChange(CHANGE_NUMBER)).isTrue();
  }

  @Test
  public void testIndexChangeFailed() throws Exception {
    setUpMocksForIndex(INDEX_OP, FAILED, ERROR_MSG, DO_NOT_THROW_EXCEPTION);
    assertThat(eventForwarder.indexChange(CHANGE_NUMBER)).isFalse();
  }

  @Test
  public void testIndexChangeThrowsException() throws Exception {
    setUpMocksForIndex(INDEX_OP, FAILED, EXCEPTION_MSG, THROW_EXCEPTION);
    assertThat(eventForwarder.indexChange(CHANGE_NUMBER)).isFalse();
  }

  @Test
  public void testChangeDeletedFromIndexOK() throws Exception {
    setUpMocksForIndex(DELETE_OP, SUCCESSFUL, EMPTY_MSG,
        DO_NOT_THROW_EXCEPTION);
    assertThat(eventForwarder.deleteChangeFromIndex(CHANGE_NUMBER)).isTrue();
  }

  @Test
  public void testChangeDeletedFromIndexFailed() throws Exception {
    setUpMocksForIndex(DELETE_OP, FAILED, ERROR_MSG, DO_NOT_THROW_EXCEPTION);
    assertThat(eventForwarder.deleteChangeFromIndex(CHANGE_NUMBER)).isFalse();
  }

  @Test
  public void testChangeDeletedFromThrowsException() throws Exception {
    setUpMocksForIndex(DELETE_OP, FAILED, EXCEPTION_MSG, THROW_EXCEPTION);
    assertThat(eventForwarder.deleteChangeFromIndex(CHANGE_NUMBER)).isFalse();
  }

  private void setUpMocksForIndex(String operation, boolean isOperationSuccessful,
      String msg, boolean exception) throws Exception {
    String request =
        Joiner.on("/").join("/plugins", PLUGIN_NAME, INDEX_OP, CHANGE_NUMBER);
    HttpSession httpSession = mock(HttpSession.class);
    if (exception) {
      if (operation.equals(INDEX_OP)) {
        doThrow(new IOException()).when(httpSession).post(request);
      } else {
        doThrow(new IOException()).when(httpSession).delete(request);
      }
    } else {
      HttpResult result = new HttpResult(isOperationSuccessful, msg);
      if (operation.equals(INDEX_OP)) {
        when(httpSession.post(request)).thenReturn(result);
      } else {
        when(httpSession.delete(request)).thenReturn(result);
      }
    }
    eventForwarder = new RestEventForwarder(httpSession, PLUGIN_NAME);
  }

  @Test
  public void testEventSentOK() throws Exception {
    Event event = setUpMocksForEvent(true, "", false);
    assertThat(eventForwarder.send(event)).isTrue();
  }

  @Test
  public void testEventSentFailed() throws Exception {
    Event event = setUpMocksForEvent(false, "Error", false);
    assertThat(eventForwarder.send(event)).isFalse();
  }

  @Test
  public void testEventSentThrowsException() throws Exception {
    Event event = setUpMocksForEvent(false, "Exception", true);
    assertThat(eventForwarder.send(event)).isFalse();
  }

  private Event setUpMocksForEvent(boolean isOperationSuccessful, String msg,
      boolean exception) throws Exception {
    Event event = new EventTest();
    String content = new GsonBuilder().create().toJson(event);
    HttpSession httpSession = mock(HttpSession.class);
    String request = Joiner.on("/").join("/plugins", PLUGIN_NAME, "event");
    if (exception) {
      doThrow(new IOException()).when(httpSession).post(request, content);
    } else {
      HttpResult result = new HttpResult(isOperationSuccessful, msg);
      when(httpSession.post(request, content)).thenReturn(result);
    }
    eventForwarder = new RestEventForwarder(httpSession, PLUGIN_NAME);
    return event;
  }

  private class EventTest extends Event {
    public EventTest() {
      super("test-event");
    }
  }
}
