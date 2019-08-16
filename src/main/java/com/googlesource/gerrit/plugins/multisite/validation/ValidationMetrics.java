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

import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.MultiSiteMetrics;

@Singleton
public class ValidationMetrics extends MultiSiteMetrics {
  private static final String GIT_UPDATE_SPLIT_BRAIN_PREVENTED = "git_update_split_brain_prevented";
  private static final String GIT_UPDATE_SPLIT_BRAIN = "git_update_split_brain";

  private final Counter1<String> splitBrainPreventionCounter;
  private final Counter1<String> splitBrainCounter;

  @Inject
  public ValidationMetrics(MetricMaker metricMaker) {
    this.splitBrainPreventionCounter =
        metricMaker.newCounter(
            "multi_site/validation/git_update_split_brain_prevented",
            rateDescription("errors", "Rate of REST API error responses"),
            stringField(
                GIT_UPDATE_SPLIT_BRAIN_PREVENTED,
                "Ref-update operations, split-brain detected and prevented"));

    this.splitBrainCounter =
        metricMaker.newCounter(
            "multi_site/validation/git_update_split_brain",
            rateDescription("errors", "Rate of REST API error responses"),
            stringField(
                GIT_UPDATE_SPLIT_BRAIN,
                "Ref-update operation left node in a split-brain scenario"));
  }

  public void incrementSplitBrainPrevention() {
    splitBrainPreventionCounter.increment(GIT_UPDATE_SPLIT_BRAIN_PREVENTED);
  }

  public void incrementSplitBrain() {
    splitBrainCounter.increment(GIT_UPDATE_SPLIT_BRAIN);
  }
}
