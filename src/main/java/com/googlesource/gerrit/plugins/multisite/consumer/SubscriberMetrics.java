package com.googlesource.gerrit.plugins.multisite.consumer;

import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SubscriberMetrics {
  private static final String SUBSCRIBER_SUCCESS_COUNTER = "subscriber_msg_consumer_counter";
  private static final String SUBSCRIBER_FAILURE_COUNTER =
      "subscriber_msg_consumer_failure_counter";
  private static final String SUBSCRIBER_POLL_FAILURE_COUNTER =
      "subscriber_msg_consumer_poll_failure_counter";

  private final Counter1<String> subscriberSuccessCounter;
  private final Counter1<String> subscriberFailureCounter;
  private final Counter1<String> subscriberPollFailureCounter;

  @Inject
  public SubscriberMetrics(MetricMaker metricMaker) {

    this.subscriberSuccessCounter =
        metricMaker.newCounter(
            "multi_site/subscriber/subscriber_message_consumer_counter",
            new Description("Number of messages consumed by the subscriber")
                .setRate()
                .setUnit("messages"),
            Field.ofString(SUBSCRIBER_SUCCESS_COUNTER, "Subscriber message consumed count"));
    this.subscriberFailureCounter =
        metricMaker.newCounter(
            "multi_site/subscriber/subscriber_message_consumer_failure_counter",
            new Description("Number of failed attempts to consume by the subscriber consumer")
                .setRate()
                .setUnit("errors"),
            Field.ofString(
                SUBSCRIBER_FAILURE_COUNTER, "Subscriber failed to consume messages count"));

    this.subscriberPollFailureCounter =
        metricMaker.newCounter(
            "multi_site/subscriber/subscriber_message_consumer_poll_failure_counter",
            new Description("Number of failed attempts to poll messages by the subscriber")
                .setRate()
                .setUnit("errors"),
            Field.ofString(
                SUBSCRIBER_POLL_FAILURE_COUNTER, "Subscriber failed to poll messages count"));
  }

  public void incrementSubscriberConsumedMessage() {
    subscriberSuccessCounter.increment(SUBSCRIBER_SUCCESS_COUNTER);
  }

  public void incrementSubscriberFailedToConsumeMessage() {
    subscriberFailureCounter.increment(SUBSCRIBER_FAILURE_COUNTER);
  }

  public void incrementSubscriberFailedToPollMessages() {
    subscriberPollFailureCounter.increment(SUBSCRIBER_POLL_FAILURE_COUNTER);
  }
}
