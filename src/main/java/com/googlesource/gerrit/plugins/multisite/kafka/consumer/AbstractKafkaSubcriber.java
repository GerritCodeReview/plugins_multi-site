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

package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gson.Gson;
import com.google.gwtorm.server.OrmException;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.MessageLogger.Direction;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerGson;
import com.googlesource.gerrit.plugins.multisite.consumer.SourceAwareEventWrapper;
import com.googlesource.gerrit.plugins.multisite.consumer.SubscriberMetrics;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import java.io.IOException;
import java.util.UUID;

public abstract class AbstractKafkaSubcriber implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KafkaEventSubscriber subscriber;
  private final ForwardedEventRouter eventRouter;
  private final DynamicSet<DroppedEventListener> droppedEventListeners;
  private final Gson gson;
  private final UUID instanceId;
  private final MessageLogger msgLog;
  private SubscriberMetrics subscriberMetrics;

  public AbstractKafkaSubcriber(
      KafkaEventSubscriber subscriber,
      ForwardedEventRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      @BrokerGson Gson gson,
      @InstanceId UUID instanceId,
      MessageLogger msgLog,
      SubscriberMetrics subscriberMetrics) {
    this.eventRouter = eventRouter;
    this.droppedEventListeners = droppedEventListeners;
    this.gson = gson;
    this.instanceId = instanceId;
    this.msgLog = msgLog;
    this.subscriberMetrics = subscriberMetrics;
    this.subscriber = subscriber;
  }

  @Override
  public void run() {
    subscriber.subscribe(getTopic(), this::processRecord);
  }

  protected abstract EventTopic getTopic();

  private void processRecord(SourceAwareEventWrapper event) {

    if (event.getHeader().getSourceInstanceId().equals(instanceId)) {
      logger.atFiner().log(
          "Dropping event %s produced by our instanceId %s",
          event.toString(), instanceId.toString());
      droppedEventListeners.forEach(l -> l.onEventDropped(event));
    } else {
      try {
        msgLog.log(Direction.CONSUME, event);
        eventRouter.route(event.getEventBody(gson));
        subscriberMetrics.incrementSubscriberConsumedMessage();
      } catch (IOException e) {
        logger.atSevere().withCause(e).log(
            "Malformed event '%s': [Exception: %s]", event.getHeader().getEventType());
        subscriberMetrics.incrementSubscriberFailedToConsumeMessage();
      } catch (PermissionBackendException | OrmException | CacheNotFoundException e) {
        logger.atSevere().withCause(e).log(
            "Cannot handle message %s: [Exception: %s]", event.getHeader().getEventType());
        subscriberMetrics.incrementSubscriberFailedToConsumeMessage();
      }
    }
  }

  // Shutdown hook which can be called from a separate thread
  public void shutdown() {
    subscriber.shutdown();
  }
}
