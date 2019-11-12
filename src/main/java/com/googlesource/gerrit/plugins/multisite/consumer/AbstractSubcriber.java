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

package com.googlesource.gerrit.plugins.multisite.consumer;

import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.MessageLogger.Direction;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class AbstractSubcriber {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ForwardedEventRouter eventRouter;
  private final DynamicSet<DroppedEventListener> droppedEventListeners;
  private final UUID instanceId;
  private final MessageLogger msgLog;
  private SubscriberMetrics subscriberMetrics;
  private final Configuration cfg;
  private final String topic;

  public AbstractSubcriber(
      ForwardedEventRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      @InstanceId UUID instanceId,
      MessageLogger msgLog,
      SubscriberMetrics subscriberMetrics,
      Configuration cfg) {
    this.eventRouter = eventRouter;
    this.droppedEventListeners = droppedEventListeners;
    this.instanceId = instanceId;
    this.msgLog = msgLog;
    this.subscriberMetrics = subscriberMetrics;
    this.cfg = cfg;
    this.topic = getTopic().topic(cfg);
  }

  protected abstract EventTopic getTopic();


  public Consumer<EventMessage> getConsumer() {
    return this::processRecord;
  }

  private void processRecord(EventMessage event) {

    if (event.getHeader().sourceInstanceId.equals(instanceId)) {
      logger.atFiner().log(
          "Dropping event %s produced by our instanceId %s",
          event.toString(), instanceId.toString());
      droppedEventListeners.forEach(l -> l.onEventDropped(event));
    } else {
      try {
        msgLog.log(Direction.CONSUME, topic, event);
        eventRouter.route(event.getEvent());
        subscriberMetrics.incrementSubscriberConsumedMessage();
      } catch (IOException e) {
        logger.atSevere().withCause(e).log(
            "Malformed event '%s': [Exception: %s]", event.getHeader());
        subscriberMetrics.incrementSubscriberFailedToConsumeMessage();
      } catch (PermissionBackendException | CacheNotFoundException e) {
        logger.atSevere().withCause(e).log(
            "Cannot handle message %s: [Exception: %s]", event.getHeader());
        subscriberMetrics.incrementSubscriberFailedToConsumeMessage();
      }
    }
  }
}
