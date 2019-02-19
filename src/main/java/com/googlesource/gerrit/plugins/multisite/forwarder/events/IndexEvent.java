package com.googlesource.gerrit.plugins.multisite.forwarder.events;

public abstract class IndexEvent extends MultiSiteEvent {
  protected IndexEvent(String type) {
    super(type);
  }
}
