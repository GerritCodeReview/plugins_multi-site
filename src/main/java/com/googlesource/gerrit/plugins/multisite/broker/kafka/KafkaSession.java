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

import static com.googlesource.gerrit.plugins.multisite.MultiSiteLogFile.multisiteLog;

import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerSession;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public class KafkaSession implements BrokerSession {
  private final Configuration properties;
  private final UUID instanceId;
  private volatile Producer<String, String> producer;

  @Inject
  public KafkaSession(Configuration configuration, @InstanceId UUID instanceId) {
    this.properties = configuration;
    this.instanceId = instanceId;
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
      multisiteLog.debug("Already connected.");
      return;
    }

    multisiteLog.info("Connect to {}...", properties.getKafka().getBootstrapServers());
    /* Need to make sure that the thread of the running connection uses
     * the correct class loader otherwize you can endup with hard to debug
     * ClassNotFoundExceptions
     */
    setConnectionClassLoader();
    producer = new KafkaProducer<>(properties.kafkaPublisher());
    multisiteLog.info("Connection established.");
  }

  private void setConnectionClassLoader() {
    Thread.currentThread().setContextClassLoader(KafkaSession.class.getClassLoader());
  }

  @Override
  public void disconnect() {
    multisiteLog.info("Disconnecting...");
    if (producer != null) {
      multisiteLog.info("Closing Producer {}...", producer);
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
      multisiteLog.debug("The offset of the record we just sent is: {}", metadata.offset());
      return true;
    } catch (InterruptedException | ExecutionException e) {
      multisiteLog.error("Cannot send the message", e);
      return false;
    }
  }
}
