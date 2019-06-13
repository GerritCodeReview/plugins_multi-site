package com.googlesource.gerrit.plugins.multisite.event.subscriber;

import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.kafka.consumer.SourceAwareEventWrapper;
import java.util.function.Consumer;

@Singleton
public class EventSubscriberNoOp implements EventSubscriber {

  @Override
  public void subscribe(
      EventFamily eventFamily, Consumer<SourceAwareEventWrapper> messageProcessor) {}

  @Override
  public void shutdown() {}
}
