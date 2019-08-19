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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.event.subscriber.EventSubscriber;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.kafka.KafkaConfiguration;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Deserializer;

public class KafkaEventSubscriber implements EventSubscriber {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Consumer<byte[], byte[]> consumer;
  private final OneOffRequestContext oneOffCtx;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final Deserializer<SourceAwareEventWrapper> valueDeserializer;
  private final KafkaConfiguration configuration;

  @Inject
  public KafkaEventSubscriber(
      KafkaConfiguration configuration,
      KafkaConsumerFactory consumerFactory,
      Deserializer<byte[]> keyDeserializer,
      Deserializer<SourceAwareEventWrapper> valueDeserializer,
      @InstanceId UUID instanceId,
      OneOffRequestContext oneOffCtx) {

    this.configuration = configuration;
    this.oneOffCtx = oneOffCtx;

    final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(KafkaEventSubscriber.class.getClassLoader());
      this.consumer = consumerFactory.create(keyDeserializer, instanceId);
    } finally {
      Thread.currentThread().setContextClassLoader(previousClassLoader);
    }
    this.valueDeserializer = valueDeserializer;
  }

  @Override
  public void subscribe(
      EventFamily eventFamily,
      java.util.function.Consumer<SourceAwareEventWrapper> messageProcessor) {
    try {
      final String topic = configuration.getKafka().getTopic(eventFamily);
      logger.atInfo().log(
          "Kafka consumer subscribing to topic [%s] for event family [%s]", topic, eventFamily);
      consumer.subscribe(Collections.singleton(topic));
      while (!closed.get()) {
        ConsumerRecords<byte[], byte[]> consumerRecords =
            consumer.poll(Duration.ofMillis(configuration.kafkaSubscriber().getPollingInterval()));
        consumerRecords.forEach(record -> processRecord(record, messageProcessor));
      }
    } catch (WakeupException e) {
      // Ignore exception if closing
      if (!closed.get()) throw e;
    } finally {
      consumer.close();
    }
  }

  private void processRecord(
      ConsumerRecord<byte[], byte[]> consumerRecord,
      java.util.function.Consumer<SourceAwareEventWrapper> messageProcessor) {
    try (ManualRequestContext ctx = oneOffCtx.open()) {

      SourceAwareEventWrapper event =
          valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value());

      messageProcessor.accept(event);
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Malformed event '%s': [Exception: %s]", new String(consumerRecord.value(), UTF_8));
    }
  }

  // Shutdown hook which can be called from a separate thread
  @Override
  public void shutdown() {
    closed.set(true);
    consumer.wakeup();
  }
}
