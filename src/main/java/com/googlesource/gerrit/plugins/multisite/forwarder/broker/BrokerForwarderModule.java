package com.googlesource.gerrit.plugins.multisite.forwarder.broker;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEvictionForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.ProjectListUpdateForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.StreamEventForwarder;

public class BrokerForwarderModule extends LifecycleModule {
  @Override
  protected void configure() {
    DynamicSet.bind(binder(), IndexEventForwarder.class).to(BrokerIndexEventForwarder.class);
    DynamicSet.bind(binder(), CacheEvictionForwarder.class).to(BrokerCacheEvictionForwarder.class);
    DynamicSet.bind(binder(), ProjectListUpdateForwarder.class)
        .to(BrokerProjectListUpdateForwarder.class);
    DynamicSet.bind(binder(), StreamEventForwarder.class).to(BrokerStreamEventForwarder.class);
  }
}
