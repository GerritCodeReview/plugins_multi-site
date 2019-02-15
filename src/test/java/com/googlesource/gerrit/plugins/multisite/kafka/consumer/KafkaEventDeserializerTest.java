package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Supplier;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventDeserializer;
import com.google.gerrit.server.events.SupplierDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provider;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class KafkaEventDeserializerTest {
  private KafkaEventDeserializer deserializer;

  @Before
  public void setUp() {
    final Provider<Gson> gsonProvider = buildGsonProvider();
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

  private Provider<Gson> buildGsonProvider() {
    Gson gson =
        new GsonBuilder()
            .registerTypeAdapter(Event.class, new EventDeserializer())
            .registerTypeAdapter(Supplier.class, new SupplierDeserializer())
            .create();
    return () -> gson;
  }
}
