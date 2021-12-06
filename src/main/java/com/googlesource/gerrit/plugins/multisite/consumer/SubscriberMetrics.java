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
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.MultiSiteMetrics;
import com.googlesource.gerrit.plugins.replication.RefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.RefReplicationDoneEvent;
import com.googlesource.gerrit.plugins.replication.ReplicationScheduledEvent;

@Singleton
public class SubscriberMetrics extends MultiSiteMetrics {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String SUBSCRIBER_SUCCESS_COUNTER = "subscriber_msg_consumer_counter";
  private static final String SUBSCRIBER_FAILURE_COUNTER =
      "subscriber_msg_consumer_failure_counter";
  private static final String REPLICATION_LAG_SEC =
      "multi_site/subscriber/subscriber_replication_status/sec_behind";

  private final Counter1<String> subscriberSuccessCounter;
  private final Counter1<String> subscriberFailureCounter;
  private final Provider<ReplicationStatus> replicationStatusProvider;

  @Inject
  public SubscriberMetrics(
      MetricMaker metricMaker, Provider<ReplicationStatus> replicationStatusProvider) {
    this.replicationStatusProvider = replicationStatusProvider;

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
        REPLICATION_LAG_SEC,
        Long.class,
        new Description("Replication lag (sec)").setGauge().setUnit(Description.Units.SECONDS),
        replicationStatusProvider.get()::getMaxLag);
  }

  public void incrementSubscriberConsumedMessage() {
    subscriberSuccessCounter.increment(SUBSCRIBER_SUCCESS_COUNTER);
  }

  public void incrementSubscriberFailedToConsumeMessage() {
    subscriberFailureCounter.increment(SUBSCRIBER_FAILURE_COUNTER);
  }

  public void updateReplicationStatusMetrics(EventMessage eventMessage) {
    Event event = eventMessage.getEvent();
    if (event instanceof RefReplicationDoneEvent) {
      RefReplicationDoneEvent replicationDone = (RefReplicationDoneEvent) event;
      replicationStatusProvider.get().updateReplicationLag(replicationDone.getProjectNameKey());
    } else if (event instanceof RefReplicatedEvent) {
      RefReplicatedEvent replicated = (RefReplicatedEvent) event;
      replicationStatusProvider.get().updateReplicationLag(replicated.getProjectNameKey());
    } else if (event instanceof ReplicationScheduledEvent) {
      ReplicationScheduledEvent updated = (ReplicationScheduledEvent) event;
      replicationStatusProvider.get().updateReplicationLag(updated.getProjectNameKey());
    } else if (event instanceof RefUpdatedEvent) {
      RefUpdatedEvent updated = (RefUpdatedEvent) event;
      replicationStatusProvider.get().updateReplicationLag(updated.getProjectNameKey());
    }
  }
}
