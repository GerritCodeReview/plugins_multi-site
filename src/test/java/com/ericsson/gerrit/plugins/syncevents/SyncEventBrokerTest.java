// Copyright (C) 2016 Ericsson
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

import com.google.gerrit.common.EventListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.events.Event;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

public class SyncEventBrokerTest extends EasyMockSupport {

  private EventListener listenerMock;
  private SyncEventBroker broker;
  private Event event = new Event(null) {};

  @Before
  public void setUp() {
    listenerMock = createMock(EventListener.class);
    DynamicSet<EventListener> listeners = DynamicSet.emptySet();
    listeners.add(listenerMock);
    broker = new SyncEventBroker(null, listeners, null, null);
  }

  @Test
  public void shouldDispatchEvent() {
    listenerMock.onEvent(event);
    replayAll();
    broker.fireEventForUnrestrictedListeners(event);
    verifyAll();
  }

  @Test
  public void shouldNotDispatchForwardedEvents() {
    replayAll();
    Context.setForwardedEvent();
    try {
      broker.fireEventForUnrestrictedListeners(event);
    } finally {
      Context.unsetForwardedEvent();
    }
    verifyAll();
  }
}
