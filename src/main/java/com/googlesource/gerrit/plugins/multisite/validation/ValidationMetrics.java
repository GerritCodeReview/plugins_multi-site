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
// Copyright (C) 2018 The Android Open Source Project
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
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ValidationMetrics {
  private static final String REF_UPDATES = "ref_updates";

  private final Counter1<String> splitBrain;

  @Inject
  public ValidationMetrics(MetricMaker metricMaker) {
    this.splitBrain =
        metricMaker.newCounter(
            "multi_site/validation/split_brain",
            new Description("Rate of REST API error responses").setRate().setUnit("errors"),
            Field.ofString(
                REF_UPDATES, "Ref-update operations detected as leading to split-brain"));
  }

  public void incrementSplitBrainRefUpdates() {
    splitBrain.increment(REF_UPDATES);
  }
}
