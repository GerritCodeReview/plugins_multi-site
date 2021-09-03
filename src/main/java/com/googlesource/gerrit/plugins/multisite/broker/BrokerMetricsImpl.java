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

package com.googlesource.gerrit.plugins.multisite.broker;

import com.gerritforge.gerrit.eventbroker.metrics.BrokerMetrics;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.MultiSiteMetrics;

public class BrokerMetricsImpl extends MultiSiteMetrics implements BrokerMetrics {
  private static final String PUBLISHER_SUCCESS_COUNTER = "broker_msg_publisher_counter";
  private static final String PUBLISHER_FAILURE_COUNTER = "broker_msg_publisher_failure_counter";

  private final Counter1<String> brokerPublisherSuccessCounter;
  private final Counter1<String> brokerPublisherFailureCounter;

  @Inject
  public BrokerMetricsImpl(MetricMaker metricMaker) {

    this.brokerPublisherSuccessCounter =
        metricMaker.newCounter(
            "multi_site/broker/broker_message_publisher_counter",
            rateDescription("messages", "Number of messages published by the broker publisher"),
            stringField(PUBLISHER_SUCCESS_COUNTER, "Broker message published count"));

    this.brokerPublisherFailureCounter =
        metricMaker.newCounter(
            "multi_site/broker/broker_message_publisher_failure_counter",
            rateDescription(
                "errors", "Number of messages failed to publish by the broker publisher"),
            stringField(PUBLISHER_FAILURE_COUNTER, "Broker failed to publish message count"));
  }

  @Override
  public void incrementBrokerPublishedMessage() {
    brokerPublisherSuccessCounter.increment(PUBLISHER_SUCCESS_COUNTER);
  }

  @Override
  public void incrementBrokerFailedToPublishMessage() {
    brokerPublisherFailureCounter.increment(PUBLISHER_FAILURE_COUNTER);
  }
}
