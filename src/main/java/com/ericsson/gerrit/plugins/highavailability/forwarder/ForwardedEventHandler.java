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

import com.google.gerrit.common.EventDispatcher;
import com.google.gerrit.server.events.Event;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatch event to the {@link EventDispatcher}. This class is meant to be used on the receiving
 * side of the {@link Forwarder} since it will prevent dispatched event to be forwarded again
 * causing an infinite forwarding loop between the 2 nodes.
 */
@Singleton
public class ForwardedEventHandler {
  private static final Logger logger = LoggerFactory.getLogger(ForwardedEventHandler.class);

  private final EventDispatcher dispatcher;

  @Inject
  public ForwardedEventHandler(EventDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /**
   * Dispatch an event in the local node, event will not be forwarded to the other node.
   *
   * @param event The event to dispatch
   * @throws OrmException If an error occur while retrieving the change the event belongs to.
   */
  public void dispatch(Event event) throws OrmException {
    try {
      Context.setForwardedEvent(true);
      logger.debug("dispatching event {}", event.getType());
      dispatcher.postEvent(event);
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}
