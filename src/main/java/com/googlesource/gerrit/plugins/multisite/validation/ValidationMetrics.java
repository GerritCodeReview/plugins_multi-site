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

package com.googlesource.gerrit.plugins.multisite.validation;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.LocalStatusStore;

@Singleton
public class ValidationMetrics {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String GIT_UPDATE_SPLIT_BRAIN_PREVENTED = "git_update_split_brain_prevented";
  private static final String GIT_UPDATE_SPLIT_BRAIN = "git_update_split_brain";
  private static final String PROJECT_REF_UPDATED_TIME_METRIC_PREFIX = "multi_site/producer/producer_ref_updated/ref_updated_epochtime_secs_";

  private final Counter1<String> splitBrainPreventionCounter;
  private final Counter1<String> splitBrainCounter;

  private LocalStatusStore localStatusStore;
  private MetricMaker metricMaker;
  private MetricRegistry metricRegistry;

  @Inject
  public ValidationMetrics(MetricMaker metricMaker, MetricRegistry metricRegistry, LocalStatusStore localStatusStore) {

    this.localStatusStore = localStatusStore;
    this.metricMaker = metricMaker;
    this.metricRegistry = metricRegistry;

    this.splitBrainPreventionCounter =
        metricMaker.newCounter(
            "multi_site/validation/git_update_split_brain_prevented",
            new Description("Rate of REST API error responses").setRate().setUnit("errors"),
            Field.ofString(
                GIT_UPDATE_SPLIT_BRAIN_PREVENTED,
                "Ref-update operations, split-brain detected and prevented"));

    this.splitBrainCounter =
        metricMaker.newCounter(
            "multi_site/validation/git_update_split_brain",
            new Description("Rate of REST API error responses").setRate().setUnit("errors"),
            Field.ofString(
                GIT_UPDATE_SPLIT_BRAIN,
                "Ref-update operation left node in a split-brain scenario"));


  }

  public void updateRefUpdatedStatusMetricsFor(String projectName) {
    localStatusStore.updateRefUpdatedTimeFor(projectName);
    String metricName = PROJECT_REF_UPDATED_TIME_METRIC_PREFIX + projectName;
    if (metricRegistry.getGauges(MetricFilter.contains(metricName)).isEmpty()) {
      metricMaker.newCallbackMetric(
              metricName,
              Long.class,
              new Description(String.format("%s ref-updated timestamp (ms)", metricName))
                      .setGauge()
                      .setUnit(Description.Units.MILLISECONDS),
              () -> localStatusStore.getLastReplicationTime(projectName).orElse(0L));
      logger.atInfo().log("Added ref-updated timestamp callback metric for " + projectName);
    } else {
      logger.atInfo().log("Don't add ref-updated metric since it already exists for project " + projectName);
    }
  }

  public void incrementSplitBrainPrevention() {
    splitBrainPreventionCounter.increment(GIT_UPDATE_SPLIT_BRAIN_PREVENTED);
  }

  public void incrementSplitBrain() {
    splitBrainCounter.increment(GIT_UPDATE_SPLIT_BRAIN);
  }
}
