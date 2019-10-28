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

package com.googlesource.gerrit.plugins.multisite.consumer;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.EventConsumer;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class MultiSiteConsumerRunner implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DynamicSet<EventConsumer> consumers;
  private DynamicItem<BrokerApi> brokerApi;

  @Inject
  public MultiSiteConsumerRunner(
      DynamicItem<BrokerApi> brokerApi, DynamicSet<EventConsumer> consumers) {
    this.consumers = consumers;
    this.brokerApi = brokerApi;
  }

  @Override
  public void start() {
    logger.atInfo().log("starting consumers");
    brokerApi.get().reconnect(Lists.newArrayList(consumers.iterator()));
  }

  @Override
  public void stop() {}
}
