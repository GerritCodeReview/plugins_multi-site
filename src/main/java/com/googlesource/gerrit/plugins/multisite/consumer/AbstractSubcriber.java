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

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.MessageLogger.Direction;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import java.io.IOException;
import java.util.function.Consumer;

public abstract class AbstractSubcriber {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ForwardedEventRouter eventRouter;
  private final DynamicSet<DroppedEventListener> droppedEventListeners;
  private final String instanceId;
  private final MessageLogger msgLog;
  private SubscriberMetrics subscriberMetrics;
  private final Configuration cfg;
  private final String topic;

  public AbstractSubcriber(
      ForwardedEventRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      @GerritInstanceId String gerritInstanceId,
      MessageLogger msgLog,
      SubscriberMetrics subscriberMetrics,
      Configuration cfg) {
    this.eventRouter = eventRouter;
    this.droppedEventListeners = droppedEventListeners;
    this.instanceId = gerritInstanceId;
    this.msgLog = msgLog;
    this.subscriberMetrics = subscriberMetrics;
    this.cfg = cfg;
    this.topic = getTopic().topic(cfg);
  }

  protected abstract EventTopic getTopic();

  protected abstract Boolean shouldConsumeEvent(Event event);

  public Consumer<Event> getConsumer() {
    return this::processRecord;
  }

  private void processRecord(Event event) {
    String sourceInstanceId = event.instanceId;

    if (Strings.isNullOrEmpty(sourceInstanceId) || instanceId.equals(sourceInstanceId)) {
      if (Strings.isNullOrEmpty(sourceInstanceId)) {
        logger.atWarning().log(
            String.format(
                "Dropping event %s because sourceInstanceId cannot be null", event.toString()));
      } else {
        logger.atFiner().log(
            String.format(
                "Dropping event %s produced by our instanceId %s", event.toString(), instanceId));
      }
      droppedEventListeners.forEach(l -> l.onEventDropped(event));
    } else {
      try {
        if (shouldConsumeEvent(event)) {
          msgLog.log(Direction.CONSUME, topic, event);
          eventRouter.route(event);
          subscriberMetrics.incrementSubscriberConsumedMessage();
          subscriberMetrics.updateReplicationStatusMetrics(event);
        }
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("Malformed event '%s'", event);
        subscriberMetrics.incrementSubscriberFailedToConsumeMessage();
      } catch (PermissionBackendException | CacheNotFoundException e) {
        logger.atSevere().withCause(e).log("Cannot handle message '%s'", event);
        subscriberMetrics.incrementSubscriberFailedToConsumeMessage();
      }
    }
  }
}
