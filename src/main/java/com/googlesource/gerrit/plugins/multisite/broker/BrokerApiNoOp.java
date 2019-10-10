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

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.gerritforge.gerrit.eventbroker.SourceAwareEventWrapper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import java.util.function.Consumer;

public class BrokerApiNoOp implements BrokerApi {
  private final Multimap<String, Consumer<SourceAwareEventWrapper>> consumersMap;

  @Inject
  public BrokerApiNoOp() {
    consumersMap = HashMultimap.create();
  }

  @Override
  public boolean send(String topic, Event event) {
    return true;
  }

  @Override
  public void receiveAsync(String topic, Consumer<SourceAwareEventWrapper> eventConsumer) {
    consumersMap.put(topic, eventConsumer);
  }

  @Override
  public void disconnect() {
    consumersMap.clear();
  }

  @Override
  public Multimap<String, Consumer<SourceAwareEventWrapper>> consumersMap() {
    return ImmutableMultimap.copyOf(consumersMap);
  }
}
