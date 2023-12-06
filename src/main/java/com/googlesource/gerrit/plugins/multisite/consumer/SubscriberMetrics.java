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

import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.logging.Metadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.MultiSiteMetrics;
import com.googlesource.gerrit.plugins.replication.events.ProjectDeletionReplicationSucceededEvent;
import com.googlesource.gerrit.plugins.replication.events.RefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.events.RefReplicationDoneEvent;
import com.googlesource.gerrit.plugins.replication.events.ReplicationScheduledEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class SubscriberMetrics extends MultiSiteMetrics {
  private static final String SUBSCRIBER_SUCCESS_COUNTER = "subscriber_msg_consumer_counter";
  private static final String SUBSCRIBER_FAILURE_COUNTER =
      "subscriber_msg_consumer_failure_counter";
  public static final String REPLICATION_LAG_SEC =
      "multi_site/subscriber/subscriber_replication_status/sec_behind";
  private static final String REPLICATION_LAG_MSEC =
      "multi_site/subscriber/subscriber_replication_status/msec_behind";
  private static final String REPLICATION_LAG_MSEC_PROJECT =
      "multi_site/subscriber/subscriber_replication_status/msec_behind/per_project";

  private final Counter1<String> subscriberSuccessCounter;
  private final Counter1<String> subscriberFailureCounter;
  private final ReplicationStatus replicationStatus;
  private static final Pattern isValidMetricNamePattern = Pattern.compile("[a-zA-Z0-9_-]");
  private static final Field<String> PROJECT_NAME =
      Field.ofString("project_name", Metadata.Builder::cacheName).build();

  @Inject
  public SubscriberMetrics(MetricMaker metricMaker, ReplicationStatus replicationStatus) {
    this.replicationStatus = replicationStatus;

    this.subscriberSuccessCounter =
        registerMetric(
            metricMaker.newCounter(
                "multi_site/subscriber/subscriber_message_consumer_counter",
                new Description("Number of messages consumed by the subscriber")
                    .setRate()
                    .setUnit("messages"),
                stringField(SUBSCRIBER_SUCCESS_COUNTER, "Subscriber message consumed count")));
    this.subscriberFailureCounter =
        registerMetric(
            metricMaker.newCounter(
                "multi_site/subscriber/subscriber_message_consumer_failure_counter",
                new Description("Number of messages failed to consume by the subscriber consumer")
                    .setRate()
                    .setUnit("errors"),
                stringField(
                    SUBSCRIBER_FAILURE_COUNTER, "Subscriber failed to consume messages count")));
    registerMetric(
        metricMaker.newCallbackMetric(
            REPLICATION_LAG_SEC,
            Long.class,
            new Description("Replication lag (sec)").setGauge().setUnit(Description.Units.SECONDS),
            replicationStatus::getMaxLag));
    registerMetric(
        metricMaker.newCallbackMetric(
            REPLICATION_LAG_MSEC,
            Long.class,
            new Description("Replication lag (msec)")
                .setGauge()
                .setUnit(Description.Units.MILLISECONDS),
            replicationStatus::getMaxLagMillis));

    CallbackMetric1<String, Long> metrics =
        registerMetric(
            metricMaker.newCallbackMetric(
                SubscriberMetrics.REPLICATION_LAG_MSEC_PROJECT,
                Long.class,
                new Description("Per-project replication lag (msec)")
                    .setGauge()
                    .setUnit(Description.Units.MILLISECONDS),
                PROJECT_NAME));
    registerMetric(
        metricMaker.newTrigger(metrics, replicationStatus.replicationLagMetricPerProject(metrics)));
  }

  /**
   * Ensures that the generated metric is compatible with prometheus metric names, as the set of
   * values that represent a valid metric name are different from the ones that represent a valid
   * project name. Main differences: - All _ are replaced with __ - All characters that aren't a
   * letter(uppercase or lowercase) or a hyphen(-) are replaced with `_<hex_code>` This is to avoid
   * all chances of name clashes when modifying a project name.
   *
   * @param name name of the metric to sanitize
   * @return sanitized metric name
   */
  public static String sanitizeProjectName(String name) {
    StringBuilder sanitizedName = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      Character c = name.charAt(i);
      Matcher matcher = isValidMetricNamePattern.matcher(String.valueOf(c));
      if (matcher.find()) {
        if (c == '_') {
          sanitizedName.append("__");
        } else {
          sanitizedName.append(c);
        }
      } else {
        sanitizedName.append("_").append(Integer.toHexString((int) c));
      }
    }
    return sanitizedName.toString();
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
      replicationStatus.updateReplicationLag(projectEvent.getProjectNameKey());
    } else if (event instanceof ProjectDeletionReplicationSucceededEvent) {
      ProjectDeletionReplicationSucceededEvent projectDeletion =
          (ProjectDeletionReplicationSucceededEvent) event;
      replicationStatus.removeProjectFromReplicationLagMetrics(projectDeletion.getProjectNameKey());
    }
  }
}
