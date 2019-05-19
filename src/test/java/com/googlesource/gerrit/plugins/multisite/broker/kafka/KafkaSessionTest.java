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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration.Kafka;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KafkaSessionTest {
  @Mock private KafkaConfiguration kafkaConfig;
  @Mock private Kafka kafka;
  @Mock private KafkaBrokerMetrics brokerMetrics;
  @Mock Producer<String, String> producer;
  @Mock Provider<Producer<String, String>> producerProvider;

  private KafkaSession objectUnderTest;

  @Before
  public void setup() {
    when(producerProvider.get()).thenReturn(producer);
    when(kafkaConfig.getKafka()).thenReturn(kafka);
    when(kafka.getTopic(EventFamily.INDEX_EVENT)).thenReturn("sample_topic");
    when(producer.send(any()))
        .thenReturn(CompletableFuture.completedFuture(createRecordMetadata()));

    objectUnderTest =
        new KafkaSession(producerProvider, kafkaConfig, UUID.randomUUID(), brokerMetrics);
    objectUnderTest.connect();
  }

  @Test
  public void shouldIncrementBrokerMetricCounterWhenMessageProducerFailed() {
    objectUnderTest.publishEvent(EventFamily.INDEX_EVENT, "sample payload");
    verify(brokerMetrics, only()).incrementBrokerProducedMessage();
  }

  @Test
  public void shouldIncrementBrokerFailedMetricCounterWhenMessageProduced() {
    when(producer.send(any()))
        .thenReturn(
            CompletableFuture.supplyAsync(
                () -> {
                  throw new RuntimeException();
                }));

    objectUnderTest.publishEvent(EventFamily.INDEX_EVENT, "sample payload");
    verify(brokerMetrics, only()).incrementBrokerFailedToProduceMessage();
  }

  private RecordMetadata createRecordMetadata() {
    TopicPartition topicPartition = new TopicPartition("topic", 0);
    return new RecordMetadata(topicPartition, 0L, 0L, 0L, 0L, 0, 0);
  }
}
