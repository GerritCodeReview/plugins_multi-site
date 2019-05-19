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

package com.googlesource.gerrit.plugins.multisite.broker.kafka;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerSession;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaSession implements BrokerSession {
  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSession.class);
  private Provider<Producer<String, String>> producerProvider;
  private KafkaConfiguration properties;
  private final UUID instanceId;
  private final KafkaBrokerMetrics brokerMetrics;
  private volatile Producer<String, String> producer;

  @Inject
  public KafkaSession(
      Provider<Producer<String, String>> producerProvider,
      KafkaConfiguration kafkaConfig,
      @InstanceId UUID instanceId,
      KafkaBrokerMetrics brokerMetrics) {
    this.producerProvider = producerProvider;
    this.properties = kafkaConfig;
    this.instanceId = instanceId;
    this.brokerMetrics = brokerMetrics;
  }

  @Override
  public boolean isOpen() {
    if (producer != null) {
      return true;
    }
    return false;
  }

  @Override
  public void connect() {
    if (isOpen()) {
      LOGGER.debug("Already connected.");
      return;
    }

    /* Need to make sure that the thread of the running connection uses
     * the correct class loader otherwize you can endup with hard to debug
     * ClassNotFoundExceptions
     */
    setConnectionClassLoader();
    producer = producerProvider.get();
    LOGGER.info("Connection established.");
  }

  private void setConnectionClassLoader() {
    Thread.currentThread().setContextClassLoader(KafkaSession.class.getClassLoader());
  }

  @Override
  public void disconnect() {
    LOGGER.info("Disconnecting...");
    if (producer != null) {
      LOGGER.info("Closing Producer {}...", producer);
      producer.close();
    }
    producer = null;
  }

  @Override
  public boolean publishEvent(EventFamily eventType, String payload) {
    return publishToTopic(properties.getKafka().getTopic(eventType), payload);
  }

  private boolean publishToTopic(String topic, String payload) {
    Future<RecordMetadata> future =
        producer.send(new ProducerRecord<>(topic, instanceId.toString(), payload));
    try {
      RecordMetadata metadata = future.get();
      LOGGER.debug("The offset of the record we just sent is: {}", metadata.offset());
      brokerMetrics.incrementBrokerProducedMessage();
      return true;
    } catch (InterruptedException | ExecutionException e) {
      LOGGER.error("Cannot send the message", e);
      brokerMetrics.incrementBrokerFailedToProduceMessage();
      return false;
    }
  }
}
