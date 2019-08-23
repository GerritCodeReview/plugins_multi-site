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

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.consumer.SourceAwareEventWrapper;
import java.util.function.Consumer;

public class BrokerApiWrapper implements BrokerApi {
  private final DynamicItem<BrokerApi> apiDelegate;
  private final BrokerMetrics metrics;

  @Inject
  public BrokerApiWrapper(DynamicItem<BrokerApi> apiDelegate, BrokerMetrics metrics) {
    this.apiDelegate = apiDelegate;
    this.metrics = metrics;
  }

  @Override
  public boolean send(String topic, Event event) {
    boolean succeeded = false;
    try {
      succeeded = apiDelegate.get().send(topic, event);
    } finally {
      if (succeeded) {
        metrics.incrementBrokerPublishedMessage();
      } else {
        metrics.incrementBrokerFailedToPublishMessage();
      }
    }
    return succeeded;
  }

  @Override
  public void receiveAync(String topic, Consumer<SourceAwareEventWrapper> eventConsumer) {
    apiDelegate.get().receiveAync(topic, eventConsumer);
  }
}
