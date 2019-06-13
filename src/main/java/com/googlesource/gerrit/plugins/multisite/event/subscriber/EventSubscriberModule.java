package com.googlesource.gerrit.plugins.multisite.event.subscriber;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Singleton
public class EventSubscriberModule extends LifecycleModule {
  @Override
  protected void configure() {
    bind(Executor.class)
        .annotatedWith(ConsumerExecutor.class)
        .toInstance(Executors.newFixedThreadPool(EventFamily.values().length));
    listener().to(MultiSiteConsumerRunner.class);

    DynamicSet.setOf(binder(), EventSubscriber.class);
  }
}
