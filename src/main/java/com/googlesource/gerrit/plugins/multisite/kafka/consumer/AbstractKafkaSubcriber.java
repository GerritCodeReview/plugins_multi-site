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

import static com.googlesource.gerrit.plugins.multisite.MultiSiteLogFile.multisiteLog;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
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

  private final KafkaConsumer<byte[], byte[]> consumer;
  private final ForwardedEventRouter eventRouter;
  private final DynamicSet<DroppedEventListener> droppedEventListeners;
  private final Provider<Gson> gsonProvider;
  private final UUID instanceId;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Deserializer<SourceAwareEventWrapper> valueDeserializer;
  private final Configuration configuration;
  private final OneOffRequestContext oneOffCtx;

  public AbstractKafkaSubcriber(
      Configuration configuration,
      Deserializer<byte[]> keyDeserializer,
      Deserializer<SourceAwareEventWrapper> valueDeserializer,
      ForwardedEventRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      Provider<Gson> gsonProvider,
      @InstanceId UUID instanceId,
      OneOffRequestContext oneOffCtx) {
    this.configuration = configuration;
    this.eventRouter = eventRouter;
    this.droppedEventListeners = droppedEventListeners;
    this.gsonProvider = gsonProvider;
    this.instanceId = instanceId;
    this.oneOffCtx = oneOffCtx;
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
      multisiteLog.info(
          "Kafka consumer subscribing to topic {} for event family {}", topic, getEventFamily());
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
    try (ManualRequestContext ctx = oneOffCtx.open()) {

      SourceAwareEventWrapper event =
          valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value());

      if (event.getHeader().getSourceInstanceId().equals(instanceId)) {
        multisiteLog.debug(
            "Dropping event {} produced by our instanceId {}",
            event.toString(),
            instanceId.toString());
        droppedEventListeners.forEach(l -> l.onEventDropped(event));
      } else {
        try {
          multisiteLog.debug("Header[{}] Body[{}]", event.getHeader(), event.getBody());
          eventRouter.route(event.getEventBody(gsonProvider));
        } catch (IOException e) {
          multisiteLog.error(
              "Malformed event '{}': [Exception: {}]", event.getHeader().getEventType(), e);
        } catch (PermissionBackendException | OrmException e) {
          multisiteLog.error(
              "Cannot handle message {}: [Exception: {}]", event.getHeader().getEventType(), e);
        }
      }
    } catch (Exception e) {
      multisiteLog.error(
          "Malformed event '{}': [Exception: {}]", new String(consumerRecord.value()), e);
    }
  }

  // Shutdown hook which can be called from a separate thread
  public void shutdown() {
    closed.set(true);
    consumer.wakeup();
  }
}
