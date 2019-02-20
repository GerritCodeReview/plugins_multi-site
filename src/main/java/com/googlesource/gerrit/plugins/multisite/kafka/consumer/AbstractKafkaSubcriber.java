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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gson.Gson;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.kafka.router.ForwardedEventRouter;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;

public abstract class AbstractKafkaSubcriber implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KafkaConsumer<byte[], byte[]> consumer;
  private final ForwardedEventRouter eventRouter;
  private final Provider<Gson> gsonProvider;
  private final UUID instanceId;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Deserializer<SourceAwareEventWrapper> valueDeserializer;
  private final Configuration configuration;

  public AbstractKafkaSubcriber(
      Configuration configuration,
      Deserializer<byte[]> keyDeserializer,
      Deserializer<SourceAwareEventWrapper> valueDeserializer,
      ForwardedEventRouter eventRouter,
      Provider<Gson> gsonProvider,
      @InstanceId UUID instanceId) {
    this.configuration = configuration;
    this.eventRouter = eventRouter;
    this.gsonProvider = gsonProvider;
    this.instanceId = instanceId;
    final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(AbstractKafkaSubcriber.class.getClassLoader());
      this.consumer =
          new KafkaConsumer<>(
              configuration.kafkaSubscriber().initPropsWith(instanceId),
              keyDeserializer,
              new ByteArrayDeserializer());
    } finally {
      Thread.currentThread().setContextClassLoader(previousClassLoader);
    }
    this.valueDeserializer = valueDeserializer;
  }

  @Override
  public void run() {
    try {
      final String topic = configuration.getKafka().getTopic(getEventFamily());
      logger.atInfo().log(
          "Kafka consumer subscribing to topic [%s] for event family [%s]",
          topic, getEventFamily());
      consumer.subscribe(Collections.singleton(topic));
      while (!closed.get()) {
        ConsumerRecords<byte[], byte[]> consumerRecords =
            consumer.poll(Duration.ofMillis(configuration.kafkaSubscriber().getPollingInterval()));
        consumerRecords.forEach(this::processRecord);
      }
    } catch (WakeupException e) {
      // Ignore exception if closing
      if (!closed.get()) throw e;
    } finally {
      consumer.close();
    }
  }

  protected abstract EventFamily getEventFamily();

  private void processRecord(ConsumerRecord<byte[], byte[]> consumerRecord) {
    try {

      SourceAwareEventWrapper event =
          valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value());

      if (event.getHeader().getSourceInstanceId().equals(instanceId)) {
        logger.atFiner().log(
            "Dropping event %s produced by our instanceId %s",
            event.toString(), instanceId.toString());
      } else {
        try {
          logger.atInfo().log("Header[%s] Body[%s]", event.getHeader(), event.getBody());
          eventRouter.route(event.getEventBody(gsonProvider));
        } catch (IOException e) {
          logger.atSevere().withCause(e).log(
              "Malformed event '%s': [Exception: %s]", event.getHeader().getEventType());
        } catch (PermissionBackendException | OrmException e) {
          logger.atSevere().withCause(e).log(
              "Cannot handle message %s: [Exception: %s]", event.getHeader().getEventType());
        }
      }
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Malformed event '%s': [Exception: %s]", new String(consumerRecord.value()));
    }
  }

  // Shutdown hook which can be called from a separate thread
  public void shutdown() {
    closed.set(true);
    consumer.wakeup();
  }
}
