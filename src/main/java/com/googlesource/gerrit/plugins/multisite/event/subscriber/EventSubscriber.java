package com.googlesource.gerrit.plugins.multisite.event.subscriber;

import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.kafka.consumer.SourceAwareEventWrapper;
import java.util.function.Consumer;

public interface EventSubscriber {
  void subscribe(EventFamily eventFamily, Consumer<SourceAwareEventWrapper> messageProcessor);

  void shutdown();
}
