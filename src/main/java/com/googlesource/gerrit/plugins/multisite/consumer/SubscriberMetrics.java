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
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import com.googlesource.gerrit.plugins.replication.RefReplicatedEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Singleton
public class SubscriberMetrics {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String SUBSCRIBER_SUCCESS_COUNTER = "subscriber_msg_consumer_counter";
  private static final String SUBSCRIBER_FAILURE_COUNTER =
      "subscriber_msg_consumer_failure_counter";
  private static final String REPLICATION_LAG_MS =
      "multi_site/subscriber/subscriber_replication_status/ms_behind";

  private final Counter1<String> subscriberSuccessCounter;
  private final Counter1<String> subscriberFailureCounter;

  public Map<String, Long> replicationStatusPerProject = new HashMap<>();

  private ProjectVersionRefUpdate projectVersionRefUpdate;

  @Inject
  public SubscriberMetrics(
      MetricMaker metricMaker, ProjectVersionRefUpdate projectVersionRefUpdate) {

    this.projectVersionRefUpdate = projectVersionRefUpdate;
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
            new Description("Number of messages failed to consume by the subscriber consumer")
                .setRate()
                .setUnit("errors"),
            Field.ofString(
                SUBSCRIBER_FAILURE_COUNTER, "Subscriber failed to consume messages count"));
    metricMaker.newCallbackMetric(
        REPLICATION_LAG_MS,
        Long.class,
        new Description("Replication lag (ms)").setGauge().setUnit(Description.Units.MILLISECONDS),
        () -> Collections.max(replicationStatusPerProject.values()));
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
      logger.atFine().log("Updating replication lag for %s", projectName);
      Optional<Long> remoteVersion = projectVersionRefUpdate.getProjectRemoteVersion(projectName);
      Optional<Long> localVersion = projectVersionRefUpdate.getProjectLocalVersion(projectName);
      if (remoteVersion.isPresent() && localVersion.isPresent()) {
        long lag = remoteVersion.get() - localVersion.get();
        logger.atFine().log("Calculated lag for project '%s' [%d]", projectName, lag);
        replicationStatusPerProject.put(projectName, lag);
      } else {
        logger.atFine().log(
            "Didn't update metric for %s. Local [%b] or remote [%b] version is not defined",
            projectName, localVersion.isPresent(), remoteVersion.isPresent());
      }
    } else {
      logger.atFine().log("Not a ref-replicated-event event [%s], skipping", event.type);
    }
  }
}
