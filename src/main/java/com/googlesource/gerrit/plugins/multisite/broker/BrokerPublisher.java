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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.MessageLogger.Direction;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.kafka.consumer.SourceAwareEventWrapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BrokerPublisher implements LifecycleListener {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  private final BrokerSession session;
  private final Gson gson;
  private final UUID instanceId;
  private final MessageLogger msgLog;

  @Inject
  public BrokerPublisher(BrokerSession session, Gson gson, @InstanceId UUID instanceId, MessageLogger msgLog) {
    this.session = session;
    this.gson = gson;
    this.instanceId = instanceId;
    this.msgLog = msgLog;
  }

  @Override
  public void start() {
    if (!session.isOpen()) {
      session.connect();
    }
  }

  @Override
  public void stop() {
    if (session.isOpen()) {
      session.disconnect();
    }
  }

  public boolean publishEvent(EventFamily eventType, Event event) {
    SourceAwareEventWrapper brokerEvent = toBrokerEvent(event);
    msgLog.log(Direction.PUBLISH, brokerEvent);
    return session.publishEvent(eventType, getPayload(brokerEvent));
  }

  private String getPayload(SourceAwareEventWrapper event) {
    return gson.toJson(event);
  }

  private SourceAwareEventWrapper toBrokerEvent(Event event) {
    JsonObject body = eventToJson(event);
    return new SourceAwareEventWrapper(
        new SourceAwareEventWrapper.EventHeader(
            UUID.randomUUID(), event.getType(), instanceId, event.eventCreatedOn),
        body);
  }

  @VisibleForTesting
  public JsonObject eventToJson(Event event) {
    return gson.toJsonTree(event).getAsJsonObject();
  }
}
