package com.googlesource.gerrit.plugins.multisite.consumer;

import com.google.common.base.Supplier;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.DisabledMetricMaker;
import org.junit.Ignore;

import java.util.Objects;

@Ignore
public class CallbackMetricMaker extends DisabledMetricMaker {
  private int replicationSecCallbackMetricCounter = 0;
  private int replicationMSecCallbackMetricCounter = 0;

  public int getReplicationSecCallbackMetricCounter() {
    return replicationSecCallbackMetricCounter;
  }
  public int getReplicationMSecCallbackMetricCounter() {
    return replicationMSecCallbackMetricCounter;
  }

  @Override
  public <V> RegistrationHandle newCallbackMetric(
      String name, Class<V> valueClass, Description desc, Supplier<V> trigger) {
    if(name.startsWith(SubscriberMetrics.REPLICATION_LAG_SEC)) {
      replicationSecCallbackMetricCounter += 1;
    }
    if(name.startsWith(SubscriberMetrics.REPLICATION_LAG_MSEC)) {
      replicationMSecCallbackMetricCounter += 1;
    }
    return new RegistrationHandle() {

      @Override
      public void remove() {}
    };
  }

  public void resetCallbackMetricCounter() {
    replicationSecCallbackMetricCounter = 0;
    replicationMSecCallbackMetricCounter = 0;
  }
}
