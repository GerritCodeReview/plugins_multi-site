package com.googlesource.gerrit.plugins.multisite.forwarder;

import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
public class NoopForwardedIndexChangeHandler extends ForwardedIndexingHandlerWithRetries<String, ChangeIndexEvent> {
  NoopForwardedIndexChangeHandler(ScheduledExecutorService indexExecutor, Configuration configuration, OneOffRequestContext oneOffCtx) {
    super(indexExecutor, configuration, oneOffCtx);
  }

  @Override
  protected void reindex(String id) {

  }

  @Override
  protected String indexName() {
    return "";
  }

  @Override
  protected void attemptToIndex(String id) {

  }

  @Override
  protected void doIndex(String id, Optional<ChangeIndexEvent> indexEvent) {
    System.out.println("===>> Noop implementation: Doing nothing!");
  }

  @Override
  protected void doDelete(String id, Optional<ChangeIndexEvent> indexEvent) {

  }
}
