// Copyright (C) 2018 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.google.gerrit.common.EventDispatcher;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectCreatedEvent;
import com.google.gwtorm.server.OrmException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedEventHandlerTest {

  @Rule public ExpectedException exception = ExpectedException.none();
  @Mock private EventDispatcher dispatcherMock;
  private ForwardedEventHandler handler;

  @Before
  public void setUp() throws Exception {
    handler = new ForwardedEventHandler(dispatcherMock);
  }

  @Test
  public void testSuccessfulDispatching() throws Exception {
    Event event = new ProjectCreatedEvent();
    handler.dispatch(event);
    verify(dispatcherMock).postEvent(event);
  }

  @Test
  public void shouldSetAndUnsetForwardedContext() throws Exception {
    Event event = new ProjectCreatedEvent();
    //this doAnswer is to allow to assert that context is set to forwarded
    //while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(dispatcherMock)
        .postEvent(event);

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.dispatch(event);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(dispatcherMock).postEvent(event);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrown() throws Exception {
    Event event = new ProjectCreatedEvent();
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new OrmException("someMessage");
                })
        .when(dispatcherMock)
        .postEvent(event);

    assertThat(Context.isForwardedEvent()).isFalse();
    try {
      handler.dispatch(event);
      fail("should have throw an OrmException");
    } catch (OrmException e) {
      assertThat(e.getMessage()).isEqualTo("someMessage");
    }
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(dispatcherMock).postEvent(event);
  }
}
