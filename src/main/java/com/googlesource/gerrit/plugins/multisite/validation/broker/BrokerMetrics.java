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

package com.googlesource.gerrit.plugins.multisite.validation.broker;

import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class BrokerMetrics {
  private static final String PRODUCER_COUNTER = "broker_msg_producer_counter";

  private final Counter1<String> brokerProducerCounter;

  @Inject
  public BrokerMetrics(MetricMaker metricMaker) {

    this.brokerProducerCounter =
        metricMaker.newCounter(
            "multi_site/validation/broker/broker_message_producer_counter",
            new Description("Number of messages produced by the broker producer")
                .setRate()
                .setUnit("messages"),
            Field.ofString(PRODUCER_COUNTER, "Broker message produced count"));
  }

  public void incrementBrokerProducedMessage() {
    brokerProducerCounter.increment(PRODUCER_COUNTER);
  }
}
