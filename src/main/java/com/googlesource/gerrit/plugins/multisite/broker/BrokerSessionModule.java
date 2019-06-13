package com.googlesource.gerrit.plugins.multisite.broker;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

@Singleton
public class BrokerSessionModule extends AbstractModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  protected void configure() {
    DynamicItem.itemOf(binder(), BrokerSession.class);
    DynamicItem.bind(binder(), BrokerSession.class).to(BrokerSessionNoOp.class);
    logger.atInfo().log("Broker engine: none");
  }
}
