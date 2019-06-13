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
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gson.Gson;
import com.google.gwtorm.server.OrmException;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.MessageLogger.Direction;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerGson;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import com.googlesource.gerrit.plugins.multisite.kafka.KafkaConfiguration;
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
  private final DynamicSet<DroppedEventListener> droppedEventListeners;
  private final Gson gson;
  private final UUID instanceId;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Deserializer<SourceAwareEventWrapper> valueDeserializer;
  private final KafkaConfiguration configuration;
  private final OneOffRequestContext oneOffCtx;
  private final MessageLogger msgLog;

  public AbstractKafkaSubcriber(
      KafkaConfiguration configuration,
      Deserializer<byte[]> keyDeserializer,
      Deserializer<SourceAwareEventWrapper> valueDeserializer,
      ForwardedEventRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      @BrokerGson Gson gson,
      @InstanceId UUID instanceId,
      OneOffRequestContext oneOffCtx,
      MessageLogger msgLog) {
    this.configuration = configuration;
    this.eventRouter = eventRouter;
    this.droppedEventListeners = droppedEventListeners;
    this.gson = gson;
    this.instanceId = instanceId;
    this.oneOffCtx = oneOffCtx;
    this.msgLog = msgLog;
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
    try (ManualRequestContext ctx = oneOffCtx.open()) {

      SourceAwareEventWrapper event =
          valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value());

      if (event.getHeader().getSourceInstanceId().equals(instanceId)) {
        logger.atFiner().log(
            "Dropping event %s produced by our instanceId %s",
            event.toString(), instanceId.toString());
        droppedEventListeners.forEach(l -> l.onEventDropped(event));
      } else {
        try {
          msgLog.log(Direction.CONSUME, event);
          eventRouter.route(event.getEventBody(gson));
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
          "Malformed event '%s': [Exception: %s]", new String(consumerRecord.value(), UTF_8));
    }
  }

  // Shutdown hook which can be called from a separate thread
  public void shutdown() {
    closed.set(true);
    consumer.wakeup();
  }
}
