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

package com.googlesource.gerrit.plugins.multisite.consumer;

import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.MultiSiteMetrics;

@Singleton
public class SubscriberMetrics extends MultiSiteMetrics {
  private static final String SUBSCRIBER_SUCCESS_COUNTER = "subscriber_msg_consumer_counter";
  private static final String SUBSCRIBER_FAILURE_COUNTER =
      "subscriber_msg_consumer_failure_counter";

  private final Counter1<String> subscriberSuccessCounter;
  private final Counter1<String> subscriberFailureCounter;

  @Inject
  public SubscriberMetrics(MetricMaker metricMaker) {

    this.subscriberSuccessCounter =
        metricMaker.newCounter(
            "multi_site/subscriber/subscriber_message_consumer_counter",
            new Description("Number of messages consumed by the subscriber")
                .setRate()
                .setUnit("messages"),
            stringField(SUBSCRIBER_SUCCESS_COUNTER, "Subscriber message consumed count"));
    this.subscriberFailureCounter =
        metricMaker.newCounter(
            "multi_site/subscriber/subscriber_message_consumer_failure_counter",
            new Description("Number of messages failed to consume by the subscriber consumer")
                .setRate()
                .setUnit("errors"),
            stringField(SUBSCRIBER_FAILURE_COUNTER, "Subscriber failed to consume messages count"));
  }

  public void incrementSubscriberConsumedMessage() {
    subscriberSuccessCounter.increment(SUBSCRIBER_SUCCESS_COUNTER);
  }

  public void incrementSubscriberFailedToConsumeMessage() {
    subscriberFailureCounter.increment(SUBSCRIBER_FAILURE_COUNTER);
  }
}
