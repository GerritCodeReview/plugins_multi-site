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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.MultiSiteMetrics;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import com.googlesource.gerrit.plugins.replication.events.ProjectDeletionReplicationSucceededEvent;
import com.googlesource.gerrit.plugins.replication.events.RefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.events.RefReplicationDoneEvent;
import com.googlesource.gerrit.plugins.replication.events.ReplicationScheduledEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
  private final ProjectVersionLogger verLogger;

  private final Map<String, Long> replicationStatusPerProject = new HashMap<>();
  private final Map<String, Long> localVersionPerProject = new HashMap<>();

  private ProjectVersionRefUpdate projectVersionRefUpdate;

  @Inject
  public SubscriberMetrics(
      MetricMaker metricMaker,
      ProjectVersionRefUpdate projectVersionRefUpdate,
      ProjectVersionLogger verLogger) {

    this.projectVersionRefUpdate = projectVersionRefUpdate;
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
        () -> {
          Collection<Long> lags = replicationStatusPerProject.values();
          if (lags.isEmpty()) {
            return 0L;
          }
          return Collections.max(lags);
        });

    this.verLogger = verLogger;
  }

  public void incrementSubscriberConsumedMessage() {
    subscriberSuccessCounter.increment(SUBSCRIBER_SUCCESS_COUNTER);
  }

  public void incrementSubscriberFailedToConsumeMessage() {
    subscriberFailureCounter.increment(SUBSCRIBER_FAILURE_COUNTER);
  }

  public void updateReplicationStatusMetrics(Event event) {

    if (event instanceof RefReplicationDoneEvent
        || event instanceof RefReplicatedEvent
        || event instanceof ReplicationScheduledEvent
        || event instanceof RefUpdatedEvent) {
      ProjectEvent projectEvent = (ProjectEvent) event;
      updateReplicationLagMetrics(projectEvent.getProjectNameKey());
    } else if (event instanceof ProjectDeletionReplicationSucceededEvent) {
      ProjectDeletionReplicationSucceededEvent projectDeletion =
          (ProjectDeletionReplicationSucceededEvent) event;
      removeProjectFromReplicationLagMetrics(projectDeletion.getProjectNameKey());
    }
  }

  private void removeProjectFromReplicationLagMetrics(Project.NameKey projectName) {
    Optional<Long> localVersion = projectVersionRefUpdate.getProjectLocalVersion(projectName.get());

    if (!localVersion.isPresent() && localVersionPerProject.containsKey(projectName.get())) {
      replicationStatusPerProject.remove(projectName.get());
      localVersionPerProject.remove(projectName.get());
      verLogger.logDeleted(projectName);
      logger.atFine().log("Removed project '%s' from replication lag metrics", projectName);
    }
  }

  private void updateReplicationLagMetrics(Project.NameKey projectName) {
    Optional<Long> remoteVersion =
        projectVersionRefUpdate.getProjectRemoteVersion(projectName.get());
    Optional<Long> localVersion = projectVersionRefUpdate.getProjectLocalVersion(projectName.get());
    if (remoteVersion.isPresent() && localVersion.isPresent()) {
      long lag = remoteVersion.get() - localVersion.get();

      if (!localVersion.get().equals(localVersionPerProject.get(projectName.get()))
          || lag != replicationStatusPerProject.get(projectName.get())) {
        logger.atFine().log(
            "Published replication lag metric for project '%s' of %d sec(s) [local-ref=%d global-ref=%d]",
            projectName, lag, localVersion.get(), remoteVersion.get());
        replicationStatusPerProject.put(projectName.get(), lag);
        localVersionPerProject.put(projectName.get(), localVersion.get());
        verLogger.log(projectName, localVersion.get(), lag);
      }
    } else {
      logger.atFine().log(
          "Did not publish replication lag metric for %s because the %s version is not defined",
          projectName, localVersion.isPresent() ? "remote" : "local");
    }
  }

  @VisibleForTesting
  Long getReplicationStatus(String projectName) {
    return replicationStatusPerProject.get(projectName);
  }

  @VisibleForTesting
  Long getLocalVersion(String projectName) {
    return localVersionPerProject.get(projectName);
  }
}
