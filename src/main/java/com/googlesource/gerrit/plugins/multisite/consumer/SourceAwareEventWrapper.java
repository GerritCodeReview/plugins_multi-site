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

import static java.util.Objects.requireNonNull;

import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.UUID;

public class SourceAwareEventWrapper {

  private final EventHeader header;
  private final JsonObject body;

  public EventHeader getHeader() {
    return header;
  }

  public JsonObject getBody() {
    return body;
  }

  public Event getEventBody(Gson gson) {
    return gson.fromJson(this.body, Event.class);
  }

  public static class EventHeader {
    private final UUID eventId;
    private final String eventType;
    private final UUID sourceInstanceId;
    private final Long eventCreatedOn;

    public EventHeader(UUID eventId, String eventType, UUID sourceInstanceId, Long eventCreatedOn) {
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

    public void validate() {
      requireNonNull(eventId, "EventId cannot be null");
      requireNonNull(eventType, "EventType cannot be null");
      requireNonNull(sourceInstanceId, "Source Instance ID cannot be null");
    }

    @Override
    public String toString() {
      return "{"
          + "eventId="
          + eventId
          + ", eventType='"
          + eventType
          + '\''
          + ", sourceInstanceId="
          + sourceInstanceId
          + ", eventCreatedOn="
          + eventCreatedOn
          + '}';
    }
  }

  public SourceAwareEventWrapper(EventHeader header, JsonObject body) {
    this.header = header;
    this.body = body;
  }

  public void validate() {
    requireNonNull(header, "Header cannot be null");
    requireNonNull(body, "Body cannot be null");
    header.validate();
  }
}
