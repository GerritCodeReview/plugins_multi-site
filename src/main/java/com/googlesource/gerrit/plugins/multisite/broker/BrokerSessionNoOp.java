package com.googlesource.gerrit.plugins.multisite.broker;

import com.google.common.flogger.FluentLogger;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;

public class BrokerSessionNoOp implements BrokerSession {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public boolean isOpen() {
    logger.atFine().log("BrokerSession NoOp - isOpen. Will return true");
    return true;
  }

  @Override
  public void connect() {
    logger.atFine().log("BrokerSession NoOp - connect()");
  }

  @Override
  public void disconnect() {
    logger.atFine().log("BrokerSession NoOp - disconnect()");
  }

  @Override
  public boolean publishEvent(EventFamily eventFamily, String payload) {
    logger.atFine().log(
        "BrokerSession NoOp - publishEvent(%s,%s). Will return true", eventFamily, payload);
    return true;
  }
}
