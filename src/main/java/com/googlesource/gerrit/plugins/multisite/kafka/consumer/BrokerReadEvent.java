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

import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provider;
import java.util.UUID;

public class BrokerReadEvent {

  private final KafkaEventHeader header;
  private final JsonObject body;

  public KafkaEventHeader getHeader() {
    return header;
  }

  public JsonObject getBody() {
    return body;
  }

  public Event getEventBody(Provider<Gson> gsonProvider) {
    return gsonProvider.get().fromJson(this.body, Event.class);
  }

  public static class KafkaEventHeader {
    private final UUID eventId;
    private final String eventType;
    private final UUID sourceInstanceId;
    private final Long eventCreatedOn;

    public KafkaEventHeader(
        UUID eventId, String eventType, UUID sourceInstanceId, Long eventCreatedOn) {
      this.eventId = eventId;
      this.eventType = eventType;
      this.sourceInstanceId = sourceInstanceId;
      this.eventCreatedOn = eventCreatedOn;
    }

    public UUID getEventId() {
      return eventId;
    }

    public String getEventType() {
      return eventType;
    }

    public UUID getSourceInstanceId() {
      return sourceInstanceId;
    }

    public Long getEventCreatedOn() {
      return eventCreatedOn;
    }
  }

  public BrokerReadEvent(KafkaEventHeader header, JsonObject body) {
    this.header = header;
    this.body = body;
  }
}
