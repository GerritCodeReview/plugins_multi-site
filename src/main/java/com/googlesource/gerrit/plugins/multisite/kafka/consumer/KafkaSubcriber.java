package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gson.Gson;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
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

@Singleton
public class KafkaSubcriber implements Runnable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final KafkaConsumer<byte[], byte[]> consumer;
  private final Configuration configuration;
  private final ForwardedEventRouter eventRouter;
  private final Provider<Gson> gsonProvider;
  private final UUID instanceId;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Deserializer<BrokerReadEvent> valueDeserializer;

  @Inject
  public KafkaSubcriber(
      Configuration configuration,
      Deserializer<byte[]> keyDeserializer,
      Deserializer<BrokerReadEvent> valueDeserializer,
      ForwardedEventRouter eventRouter,
      Provider<Gson> gsonProvider,
      @InstanceId UUID instanceId) {
    this.configuration = configuration;
    this.eventRouter = eventRouter;
    this.gsonProvider = gsonProvider;
    this.instanceId = instanceId;
    final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(KafkaSubcriber.class.getClassLoader());
      this.consumer =
          new KafkaConsumer<>(
              configuration.kafkaSubscriber().getProps(instanceId),
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
      consumer.subscribe(Collections.singletonList(configuration.kafkaSubscriber().getTopic()));
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

  private void processRecord(ConsumerRecord<byte[], byte[]> consumerRecord) {
    try {

      BrokerReadEvent event =
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
          logger.atSevere().log(
              "Malformed event '%s': [Exception: %s]", event.getHeader().getEventType(), e);
        } catch (PermissionBackendException | OrmException e) {
          logger.atSevere().log(
              "Cannot handle message %s: [Exception: %s]", event.getHeader().getEventType(), e);
        }
      }
    } catch (Exception e) {
      logger.atSevere().log(
          "Malformed event '%s': [Exception: %s]", new String(consumerRecord.value()), e);
    }
  }

  // Shutdown hook which can be called from a separate thread
  public void shutdown() {
    closed.set(true);
    consumer.wakeup();
  }
}
