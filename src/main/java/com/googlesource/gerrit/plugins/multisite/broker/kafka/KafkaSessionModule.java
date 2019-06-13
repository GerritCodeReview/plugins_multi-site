package com.googlesource.gerrit.plugins.multisite.broker.kafka;

import com.google.gerrit.lifecycle.LifecycleModule;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerSession;

public class KafkaSessionModule extends LifecycleModule {
  @Override
  protected void configure() {
    bind(BrokerSession.class).to(KafkaSession.class);
  }
}
