package com.googlesource.gerrit.plugins.multisite.consumer;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.MultiSiteEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubscriberModule extends LifecycleModule {

  @Override
  protected void configure() {
    MultiSiteEvent.registerEventTypes();
    bind(ExecutorService.class)
        .annotatedWith(ConsumerExecutor.class)
        .toInstance(Executors.newFixedThreadPool(EventTopic.values().length));
    listener().to(MultiSiteConsumerRunner.class);

    DynamicSet.setOf(binder(), AbstractSubcriber.class);
  }
}
