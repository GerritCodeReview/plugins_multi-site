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
import com.gerritforge.gerrit.eventbroker.TopicSubscriber;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.MessageLogger.Direction;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrokerApiWrapper implements BrokerApi {
  private static final Logger log = LoggerFactory.getLogger(BrokerApiWrapper.class);
  private final Executor executor;
  private final DynamicItem<BrokerApi> apiDelegate;
  private final BrokerMetrics metrics;
  private final MessageLogger msgLog;
  private final String nodeInstanceId;

  @Inject
  public BrokerApiWrapper(
      @BrokerExecutor Executor executor,
      DynamicItem<BrokerApi> apiDelegate,
      BrokerMetrics metrics,
      MessageLogger msgLog,
      @GerritInstanceId String instanceId) {
    this.apiDelegate = apiDelegate;
    this.executor = executor;
    this.metrics = metrics;
    this.msgLog = msgLog;
    this.nodeInstanceId = instanceId;
  }

  public boolean sendSync(String topic, Event event) {
    try {
      return send(topic, event).get();
    } catch (Throwable e) {
      log.error(
          "Failed to publish event '{}' to topic '{}' - error: {} - stack trace: {}",
          event,
          topic,
          e.getMessage(),
          e.getStackTrace());
      metrics.incrementBrokerFailedToPublishMessage();
      return false;
    }
  }

  @Override
  public ListenableFuture<Boolean> send(String topic, Event message) {
    SettableFuture<Boolean> resultFuture = SettableFuture.create();
    if (!nodeInstanceId.equals(message.instanceId)) {
      resultFuture.set(true);
      return resultFuture;
    }

    if (Strings.isNullOrEmpty(message.instanceId)) {
      log.warn(
          "Dropping event '{}' because event instance id cannot be null or empty",
          message.toString());
      resultFuture.set(true);
      return resultFuture;
    }

    ListenableFuture<Boolean> resfultF = apiDelegate.get().send(topic, message);
    Futures.addCallback(
        resfultF,
        new FutureCallback<Boolean>() {
          @Override
          public void onSuccess(Boolean result) {
            msgLog.log(Direction.PUBLISH, topic, message);
            metrics.incrementBrokerPublishedMessage();
          }

          @Override
          public void onFailure(Throwable throwable) {
            log.error(
                "Failed to publish message '{}' to topic '{}' - error: {}",
                message.toString(),
                topic,
                throwable.getMessage());
            metrics.incrementBrokerFailedToPublishMessage();
          }
        },
        executor);

    return resfultF;
  }

  @Override
  public void receiveAsync(String topic, Consumer<Event> messageConsumer) {
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
