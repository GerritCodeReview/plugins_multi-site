package com.googlesource.gerrit.plugins.multisite.consumer;

import com.google.common.base.Supplier;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.DisabledMetricMaker;
import org.junit.Ignore;

@Ignore
public class CallbackMetricMaker extends DisabledMetricMaker {
  private int callbackMetricCounter = 0;

  public int getCallbackMetricCounter() {
    return callbackMetricCounter;
  }

  @Override
  public <V> RegistrationHandle newCallbackMetric(
      String name, Class<V> valueClass, Description desc, Supplier<V> trigger) {
    callbackMetricCounter += 1;
    return new RegistrationHandle() {

      @Override
      public void remove() {}
    };
  }

  public void resetCallbackMetricCounter() {
    callbackMetricCounter = 0;
  }
}
