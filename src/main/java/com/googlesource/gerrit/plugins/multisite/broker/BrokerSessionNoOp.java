// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.multisite.broker;

import com.google.common.flogger.FluentLogger;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;

@Singleton
public class BrokerSessionNoOp implements BrokerSession {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public boolean isOpen() {
    logger.atFinest().log("BrokerSession NoOp - isOpen. Will return true");
    return true;
  }

  @Override
  public void connect() {
    logger.atFinest().log("BrokerSession NoOp - connect()");
  }

  @Override
  public void disconnect() {
    logger.atFinest().log("BrokerSession NoOp - disconnect()");
  }

  @Override
  public boolean publishEvent(EventFamily eventFamily, String payload) {
    logger.atFinest().log(
        "BrokerSession NoOp - publishEvent(%s,%s). Will return true", eventFamily, payload);
    return true;
  }
}
