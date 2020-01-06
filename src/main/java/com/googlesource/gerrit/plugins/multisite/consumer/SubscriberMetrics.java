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

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.MultiSiteMetrics;
import com.googlesource.gerrit.plugins.multisite.ReplicationStatusStore;
import com.googlesource.gerrit.plugins.replication.RefReplicatedEvent;

@Singleton
public class SubscriberMetrics extends MultiSiteMetrics {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String SUBSCRIBER_SUCCESS_COUNTER = "subscriber_msg_consumer_counter";
  private static final String SUBSCRIBER_FAILURE_COUNTER =
      "subscriber_msg_consumer_failure_counter";
  private static final String INSTANCE_LATEST_REPLICATION_TIME_METRICS =
      "multi_site/subscriber/instance_replication_status_latest_replication_time";
  private static final String PROJECT_LATEST_REPLICATION_TIME_PREFIX =
      "multi_site/subscriber/replication_status_latest_replication_time_";

  private ReplicationStatusStore replicationStatusStore;
  private MetricMaker metricMaker;
  private MetricRegistry metricRegistry;

  private final Counter1<String> subscriberSuccessCounter;
  private final Counter1<String> subscriberFailureCounter;

  @Inject
  public SubscriberMetrics(
      MetricMaker metricMaker,
      MetricRegistry metricRegistry,
      ReplicationStatusStore replicationStatusStore) {

    this.replicationStatusStore = replicationStatusStore;
    this.metricMaker = metricMaker;
    this.metricRegistry = metricRegistry;

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

    metricMaker.newCallbackMetric(
        INSTANCE_LATEST_REPLICATION_TIME_METRICS,
        Long.class,
        new Description(
                String.format(
                    "%s last replication timestamp (ms)", INSTANCE_LATEST_REPLICATION_TIME_METRICS))
            .setGauge()
            .setUnit(Description.Units.MILLISECONDS),
        () -> replicationStatusStore.getGlobalLastReplicationTime());
  }

  public void incrementSubscriberConsumedMessage() {
    subscriberSuccessCounter.increment(SUBSCRIBER_SUCCESS_COUNTER);
  }

  public void incrementSubscriberFailedToConsumeMessage() {
    subscriberFailureCounter.increment(SUBSCRIBER_FAILURE_COUNTER);
  }

  public void updateReplicationStatusMetrics(EventMessage eventMessage) {
    Event event = eventMessage.getEvent();
    if (event instanceof RefReplicatedEvent) {
      RefReplicatedEvent refReplicatedEvent = (RefReplicatedEvent) event;
      String projectName = refReplicatedEvent.getProjectNameKey().get();
      logger.atInfo().log("Updating last replication time for %s", projectName);
      replicationStatusStore.updateLastReplicationTime(projectName, event.eventCreatedOn);
      upsertMertricsForProject(projectName);
    } else {
      logger.atInfo().log("Not a ref-replicated-event event [%s], skipping", event.type);
    }
  }

  private void upsertMertricsForProject(String projectName) {
    String metricName = PROJECT_LATEST_REPLICATION_TIME_PREFIX + projectName;
    if (metricRegistry.getGauges(MetricFilter.contains(metricName)).isEmpty()) {
      metricMaker.newCallbackMetric(
          metricName,
          Long.class,
          new Description(String.format("%s last replication timestamp (ms)", metricName))
              .setGauge()
              .setUnit(Description.Units.MILLISECONDS),
          () -> replicationStatusStore.getLastReplicationTime(projectName).orElse(0L));
      logger.atInfo().log("Added last replication timestamp callback metric for " + projectName);
    } else {
      logger.atInfo().log("Don't add metric since it already exists for project " + projectName);
    }
  }
}
