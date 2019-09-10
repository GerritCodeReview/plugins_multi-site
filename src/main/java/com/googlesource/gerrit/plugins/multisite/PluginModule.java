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

package com.googlesource.gerrit.plugins.multisite;

import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApi;
import com.googlesource.gerrit.plugins.multisite.kafka.KafkaBrokerApi;
import com.googlesource.gerrit.plugins.multisite.kafka.KafkaBrokerModule;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectDeletedSharedDbCleanup;

public class PluginModule extends LifecycleModule {
  private Configuration config;
  private KafkaBrokerModule kafkaBrokerModule;

  @Inject
  public PluginModule(Configuration config, KafkaBrokerModule kafkaBrokerModule) {
    this.config = config;
    this.kafkaBrokerModule = kafkaBrokerModule;
  }

  @Override
  protected void configure() {
    if (config.getSharedRefDb().isEnabled()) {
      listener().to(PluginStartup.class);
      DynamicSet.bind(binder(), ProjectDeletedListener.class)
          .to(ProjectDeletedSharedDbCleanup.class);
    }
    DynamicItem.bind(binder(), BrokerApi.class).to(KafkaBrokerApi.class).in(Scopes.SINGLETON);
    listener().to(KafkaBrokerApi.class);
    install(kafkaBrokerModule);
  }
}
