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

package com.googlesource.gerrit.plugins.multisite.broker;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.google.common.base.MoreObjects;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.MessageLogger.Direction;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class BrokerApiWrapper implements BrokerApi {
  private final DynamicItem<BrokerApi> apiDelegate;
  private final BrokerMetrics metrics;
  private final MessageLogger msgLog;
  private final UUID instanceId;
  private final String gerritInstanceId;

  @Inject
  public BrokerApiWrapper(
      DynamicItem<BrokerApi> apiDelegate,
      BrokerMetrics metrics,
      MessageLogger msgLog,
      @InstanceId UUID instanceId,
      @Nullable @GerritInstanceId String gerritInstanceId) {
    this.apiDelegate = apiDelegate;
    this.metrics = metrics;
    this.msgLog = msgLog;
    this.instanceId = instanceId;
    this.gerritInstanceId = gerritInstanceId;
  }

  public boolean send(String topic, Event event) {
    event.instanceId =
        MoreObjects.firstNonNull(
            event.instanceId, MoreObjects.firstNonNull(gerritInstanceId, instanceId.toString()));
    return send(topic, apiDelegate.get().newMessage(instanceId, event));
  }

  @Override
  public boolean send(String topic, EventMessage message) {
    if (Context.isForwardedEvent()) {
      return true;
    }
    boolean succeeded = false;
    try {
      succeeded = apiDelegate.get().send(topic, message);
    } finally {
      if (succeeded) {
        msgLog.log(Direction.PUBLISH, topic, message);
        metrics.incrementBrokerPublishedMessage();
      } else {
        metrics.incrementBrokerFailedToPublishMessage();
      }
    }
    return succeeded;
  }

  @Override
  public void receiveAsync(String topic, Consumer<EventMessage> messageConsumer) {
    apiDelegate.get().receiveAsync(topic, messageConsumer);
  }

  @Override
  public void disconnect() {
    apiDelegate.get().disconnect();
  }

  @Override
  public Set<TopicSubscriber> topicSubscribers() {
    return apiDelegate.get().topicSubscribers();
  }

  @Override
  public void replayAllEvents(String topic) {
    apiDelegate.get().replayAllEvents(topic);
  }
}
