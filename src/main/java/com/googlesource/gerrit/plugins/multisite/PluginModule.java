// Copyright (C) 2015 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApi;
import com.googlesource.gerrit.plugins.multisite.broker.kafka.KafkaBrokerForwarderModule;
import com.googlesource.gerrit.plugins.multisite.kafka.KafkaBrokerApi;
import com.googlesource.gerrit.plugins.multisite.kafka.router.KafkaForwardedEventRouterModule;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZkValidationModule;

public class PluginModule extends LifecycleModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private Configuration config;
  private ZkValidationModule zkValidationModule;
  private KafkaForwardedEventRouterModule kafkaForwardedEventRouterModule;
  private KafkaBrokerForwarderModule kafkaBrokerForwarderModule;

  @Inject
  public PluginModule(
      Configuration config,
      ZkValidationModule zkValidationModule,
      KafkaForwardedEventRouterModule forwardedEeventRouterModule,
      KafkaBrokerForwarderModule brokerForwarderModule) {
    this.config = config;
    this.zkValidationModule = zkValidationModule;
    this.kafkaForwardedEventRouterModule = forwardedEeventRouterModule;
    this.kafkaBrokerForwarderModule = brokerForwarderModule;
  }

  @Override
  protected void configure() {
    if (config.getSharedRefDb().isEnabled()) {
      logger.atInfo().log("Shared ref-db engine: Zookeeper");
      install(zkValidationModule);
    }

    DynamicItem.bind(binder(), BrokerApi.class).to(KafkaBrokerApi.class).in(Scopes.SINGLETON);
    listener().to(KafkaBrokerApi.class);

    install(kafkaForwardedEventRouterModule);
    install(kafkaBrokerForwarderModule);
  }
}
