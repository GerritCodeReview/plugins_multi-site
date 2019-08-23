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
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.events.EventGson;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.MessageLogger.Direction;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import com.googlesource.gerrit.plugins.multisite.consumer.SourceAwareEventWrapper;
import com.googlesource.gerrit.plugins.multisite.consumer.SubscriberMetrics;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.kafka.KafkaConfiguration;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Deserializer;

public class KafkaEventSubscriber {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Consumer<byte[], byte[]> consumer;
  private final OneOffRequestContext oneOffCtx;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final Deserializer<SourceAwareEventWrapper> valueDeserializer;
  private final KafkaConfiguration configuration;
  private final SubscriberMetrics subscriberMetrics;

  @Inject
  public KafkaEventSubscriber(
      KafkaConfiguration configuration,
      KafkaConsumerFactory consumerFactory,
      Deserializer<byte[]> keyDeserializer,
      Deserializer<SourceAwareEventWrapper> valueDeserializer,
      @InstanceId UUID instanceId,
      OneOffRequestContext oneOffCtx,
      SubscriberMetrics subscriberMetrics) {

    this.configuration = configuration;
    this.oneOffCtx = oneOffCtx;
    this.subscriberMetrics = subscriberMetrics;

    final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(KafkaEventSubscriber.class.getClassLoader());
      this.consumer = consumerFactory.create(keyDeserializer, instanceId);
    } finally {
      Thread.currentThread().setContextClassLoader(previousClassLoader);
    }
    this.valueDeserializer = valueDeserializer;
  }

  public void subscribe(
      EventTopic evenTopic, java.util.function.Consumer<SourceAwareEventWrapper> messageProcessor) {
    try {
      final String topic = configuration.getKafka().getTopicAlias(evenTopic);
      logger.atInfo().log(
          "Kafka consumer subscribing to topic alias [%s] for event topic [%s]", topic, evenTopic);
      consumer.subscribe(Collections.singleton(topic));
      while (!closed.get()) {
        ConsumerRecords<byte[], byte[]> consumerRecords =
            consumer.poll(Duration.ofMillis(configuration.kafkaSubscriber().getPollingInterval()));
        consumerRecords.forEach(
            consumerRecord -> {
              try (ManualRequestContext ctx = oneOffCtx.open()) {
                SourceAwareEventWrapper event =
                    valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value());
                messageProcessor.accept(event);
              } catch (Exception e) {
                logger.atSevere().withCause(e).log(
                    "Malformed event '%s': [Exception: %s]",
                    new String(consumerRecord.value(), UTF_8));
                subscriberMetrics.incrementSubscriberFailedToConsumeMessage();
              }
            });
      }
    } catch (WakeupException e) {
      // Ignore exception if closing
      if (!closed.get()) throw e;
    } catch (Exception e) {
      subscriberMetrics.incrementSubscriberFailedToPollMessages();
      throw e;
    } finally {
      consumer.close();
    }
  }

  public void shutdown() {
    closed.set(true);
    consumer.wakeup();
  }
}
