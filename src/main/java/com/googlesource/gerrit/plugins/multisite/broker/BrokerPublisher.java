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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.kafka.consumer.BrokerReadEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BrokerPublisher implements LifecycleListener {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  private final BrokerSession session;
  private final Gson gson;
  private final UUID instanceId;

  @Inject
  public BrokerPublisher(BrokerSession session, Gson gson, @InstanceId UUID instanceId) {
    this.session = session;
    this.gson = gson;
    this.instanceId = instanceId;
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

  public boolean publishIndexEvent(Event event) {
    JsonObject obj = gson.toJsonTree(event).getAsJsonObject();
    BrokerReadEvent eventWithHeader =
        new BrokerReadEvent(
            new BrokerReadEvent.KafkaEventHeader(UUID.randomUUID(), "", instanceId, 1L), obj);

    return session.publishIndexEvent(gson.toJson(eventWithHeader));
  }
}
