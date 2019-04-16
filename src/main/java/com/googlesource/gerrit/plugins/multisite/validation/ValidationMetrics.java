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
