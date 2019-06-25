package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;

@Singleton
public class KafkaConsumerFactory {
  private KafkaConfiguration config;

  @Inject
  public KafkaConsumerFactory(KafkaConfiguration configuration) {
    this.config = configuration;
  }

  public Consumer<byte[], byte[]> create(Deserializer<byte[]> keyDeserializer, UUID instanceId) {
    return new KafkaConsumer<>(
        config.kafkaSubscriber().initPropsWith(instanceId),
        keyDeserializer,
        new ByteArrayDeserializer());
  }
}
