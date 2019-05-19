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

import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class KafkaBrokerMetrics {
  private static final String PRODUCER_SUCCESS_COUNTER = "kafka_broker_msg_producer_counter";
  private static final String PRODUCER_FAILURE_COUNTER =
      "kafka_broker_msg_producer_failure_counter";

  private final Counter1<String> brokerProducerSuccessCounter;
  private final Counter1<String> brokerProducerFailureCounter;

  @Inject
  public KafkaBrokerMetrics(MetricMaker metricMaker) {

    this.brokerProducerSuccessCounter =
        metricMaker.newCounter(
            "multi_site/kafka/broker/kafka_broker_message_producer_counter",
            new Description("Number of messages produced by the broker producer")
                .setRate()
                .setUnit("messages"),
            Field.ofString(PRODUCER_SUCCESS_COUNTER, "Broker message produced count"));
    this.brokerProducerFailureCounter =
        metricMaker.newCounter(
            "multi_site/kafka/broker/kafka_broker_msg_producer_failure_counter",
            new Description("Number of messages failed to produce by the broker producer")
                .setRate()
                .setUnit("errors"),
            Field.ofString(PRODUCER_FAILURE_COUNTER, "Broker failed to produce message count"));
  }

  public void incrementBrokerProducedMessage() {
    brokerProducerSuccessCounter.increment(PRODUCER_SUCCESS_COUNTER);
  }

  public void incrementBrokerFailedToProduceMessage() {
    brokerProducerFailureCounter.increment(PRODUCER_FAILURE_COUNTER);
  }
}
