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

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.Gson;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.broker.GsonProvider;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class KafkaEventDeserializerTest {
  private KafkaEventDeserializer deserializer;

  @Before
  public void setUp() {
    final Provider<Gson> gsonProvider = new GsonProvider();
    deserializer = new KafkaEventDeserializer(gsonProvider);
  }

  @Test
  public void kafkaEventDeserializerShouldParseAKafkaEvent() {
    final UUID eventId = UUID.randomUUID();
    final String eventType = "event-type";
    final UUID sourceInstanceId = UUID.randomUUID();
    final long eventCreatedOn = 10L;
    final String eventJson =
        String.format(
            "{ "
                + "\"header\": { \"eventId\": \"%s\", \"eventType\": \"%s\", \"sourceInstanceId\": \"%s\", \"eventCreatedOn\": %d },"
                + "\"body\": {}"
                + "}",
            eventId, eventType, sourceInstanceId, eventCreatedOn);
    final SourceAwareEventWrapper event = deserializer.deserialize("ignored", eventJson.getBytes());

    assertThat(event.getBody().entrySet()).isEmpty();
    assertThat(event.getHeader().getEventId()).isEqualTo(eventId);
    assertThat(event.getHeader().getEventType()).isEqualTo(eventType);
    assertThat(event.getHeader().getSourceInstanceId()).isEqualTo(sourceInstanceId);
    assertThat(event.getHeader().getEventCreatedOn()).isEqualTo(eventCreatedOn);
  }

  @Test(expected = RuntimeException.class)
  public void kafkaEventDeserializerShouldFailForInvalidJson() {
    deserializer.deserialize("ignored", "this is not a JSON string".getBytes());
  }

  @Test(expected = RuntimeException.class)
  public void kafkaEventDeserializerShouldFailForInvalidObjectButValidJSON() {
    deserializer.deserialize("ignored", "{}".getBytes());
  }
}
