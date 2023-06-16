package com.googlesource.gerrit.plugins.multisite.consumer;

import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.Field;
import org.junit.Ignore;

@Ignore
public class CallbackMetricMaker extends DisabledMetricMaker {
  private int callbackMetricCounter = 0;

  public int getCallbackMetricCounter() {
    return callbackMetricCounter;
  }

  @Override
  public <F1> Counter1<F1> newCounter(String name, Description desc, Field<F1> field1) {
    callbackMetricCounter += 1;
    return super.newCounter(name, desc, field1);
  }

  public void resetCallbackMetricCounter() {
    callbackMetricCounter = 0;
  }
}
