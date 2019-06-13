package com.googlesource.gerrit.plugins.multisite.broker;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class BrokerSessionProvider implements Provider<BrokerSession> {

  DynamicSet<BrokerSession> providers;

  @Inject
  public BrokerSessionProvider(DynamicSet<BrokerSession> providers) {
    this.providers = providers;
  }

  @Override
  public BrokerSession get() {
    List<BrokerSession> brokers = ImmutableList.copyOf(providers);
    if (brokers.size() > 2)
      throw new IllegalStateException("More than two instaces of BrokerSession found");

    if(brokers.size() == 0) throw new IllegalStateException("No implementation of BrokerSession found");

    
    Optional<BrokerSession> brokerSessionImplementation =
        brokers.stream().filter(broker -> !(broker instanceof BrokerSessionNoOp)).findFirst();

    return brokerSessionImplementation.orElse(brokers.get(0));
  }
}
