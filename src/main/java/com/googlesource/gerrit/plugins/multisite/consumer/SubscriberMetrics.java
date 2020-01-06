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

import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.metrics.CallbackMetric0;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.MultiSiteMetrics;
import com.googlesource.gerrit.plugins.multisite.StatusStore;
import com.googlesource.gerrit.plugins.replication.RefReplicatedEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class SubscriberMetrics extends MultiSiteMetrics {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String SUBSCRIBER_SUCCESS_COUNTER = "subscriber_msg_consumer_counter";
  private static final String SUBSCRIBER_FAILURE_COUNTER =
      "subscriber_msg_consumer_failure_counter";
  private static final String GLOBAL_LATENCY_METRIC = "global-latency-metric";
  private Set<String> projectsWithMetrics;

  private StatusStore statusStore;
  private MetricMaker metricMaker;

  private final Counter1<String> subscriberSuccessCounter;
  private final Counter1<String> subscriberFailureCounter;

  @Inject
  public SubscriberMetrics(MetricMaker metricMaker, StatusStore statusStore) {

    this.projectsWithMetrics = Collections.synchronizedSet(new HashSet <>());
    this.statusStore = statusStore;
    this.metricMaker = metricMaker;

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

  public void updateReplicationStatusMetrics(EventMessage eventMessage) {
    Event event = eventMessage.getEvent();
    if (event instanceof RefReplicatedEvent) {
      RefReplicatedEvent refReplicatedEvent = (RefReplicatedEvent) event;
      String projectName = refReplicatedEvent.getProjectNameKey().get();
      logger.atInfo().log("Updating last replication time for %s", projectName);
      statusStore.updateLastReplicationTime(projectName,event.eventCreatedOn);
      upsertMertricForProject(projectName);
    }
    else {
      logger.atInfo().log("Not a ref-replicated-event event [%s], skipping", event.type);
    }
  }

  private void upsertMertricForProject(String projectName) {
    if (!projectsWithMetrics.contains(projectName)) {
      addLatencyMetricFor(projectName);
    } else {
      logger.atInfo().log("Metric already exists for project " + projectName);
    }
    if (!projectsWithMetrics.contains(GLOBAL_LATENCY_METRIC)) {
      addGlobalLatencyMetric();
    } else {
      logger.atInfo().log("Metric already exists for global project");
    }
  }

  private void addGlobalLatencyMetric() {
    String name = "replicationstatus-global";
    metricMaker.newCallbackMetric(
            String.format("%s/latest_replication_time", name),
            Long.class,
            new Description(String.format("%s last replication timestamp (ms)", name))
                    .setGauge()
                    .setUnit(Description.Units.MILLISECONDS),
            () -> statusStore.getGlobalLastReplicationTime());
    projectsWithMetrics.add(GLOBAL_LATENCY_METRIC);
    logger.atInfo().log("Added callback for Global Metric");
  }

  private void addLatencyMetricFor(String projectName) {
    String name = "replicationstatus-" + projectName;
    metricMaker.newCallbackMetric(
            String.format("%s/latest_replication_time", name),
            Long.class,
            new Description(String.format("%s last replication timestamp (ms)", name))
                    .setGauge()
                    .setUnit(Description.Units.MILLISECONDS),
            () -> statusStore.getLastReplicationTime(projectName).orElse(0L));
    logger.atInfo().log("Added callback for " + projectName);
    projectsWithMetrics.add(projectName);
  }
}
