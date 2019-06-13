package com.googlesource.gerrit.plugins.multisite.event.subscriber;

public interface EventSubscriber extends Runnable {
  void shutdown();
}
