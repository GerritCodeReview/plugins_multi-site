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

import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.PluginMetadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.BiConsumer;

@Singleton
public class BrokerMetrics {
  private static final String PUBLISHER_SUCCESS_COUNTER = "broker_msg_publisher_counter";
  private static final String PUBLISHER_FAILURE_COUNTER = "broker_msg_publisher_failure_counter";

  private final Counter1<String> brokerPublisherSuccessCounter;
  private final Counter1<String> brokerPublisherFailureCounter;

  @Inject
  public BrokerMetrics(MetricMaker metricMaker) {

    this.brokerPublisherSuccessCounter =
        metricMaker.newCounter(
            "multi_site/broker/broker_message_publisher_counter",
            new Description("Number of messages published by the broker publisher")
                .setRate()
                .setUnit("messages"),
            Field.ofString(PUBLISHER_SUCCESS_COUNTER, metadataMapper(PUBLISHER_SUCCESS_COUNTER))
                .description("Broker message published count")
                .build());
    this.brokerPublisherFailureCounter =
        metricMaker.newCounter(
            "multi_site/broker/broker_message_publisher_failure_counter",
            new Description("Number of messages failed to publish by the broker publisher")
                .setRate()
                .setUnit("errors"),
            Field.ofString(PUBLISHER_FAILURE_COUNTER, metadataMapper(PUBLISHER_FAILURE_COUNTER))
                .description("Broker failed to publish message count")
                .build());
  }

  public void incrementBrokerPublishedMessage() {
    brokerPublisherSuccessCounter.increment(PUBLISHER_SUCCESS_COUNTER);
  }

  public void incrementBrokerFailedToPublishMessage() {
    brokerPublisherFailureCounter.increment(PUBLISHER_FAILURE_COUNTER);
  }

  private BiConsumer<Metadata.Builder, String> metadataMapper(String metadataKey) {
    return (metadataBuilder, fieldValue) ->
        metadataBuilder.addPluginMetadata(PluginMetadata.create(metadataKey, fieldValue));
  }
}
