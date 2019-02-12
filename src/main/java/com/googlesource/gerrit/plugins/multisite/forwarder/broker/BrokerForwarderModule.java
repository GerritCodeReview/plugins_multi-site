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

package com.googlesource.gerrit.plugins.multisite.forwarder.broker;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerPublisher;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerSession;
import com.googlesource.gerrit.plugins.multisite.broker.GsonProvider;
import com.googlesource.gerrit.plugins.multisite.broker.kafka.KafkaSession;
import com.googlesource.gerrit.plugins.multisite.forwarder.Forwarder;

public class BrokerForwarderModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Gson.class).toProvider(GsonProvider.class).in(Singleton.class);
    DynamicSet.bind(binder(), LifecycleListener.class).to(BrokerPublisher.class);
    bind(Forwarder.class).to(BrokerForwarder.class);
    bind(BrokerSession.class).to(KafkaSession.class);
  }
}
