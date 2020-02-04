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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApiWrapper;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerGson;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.CacheEvictionEventRouter;
import java.util.UUID;

@Singleton
public class CacheEvictionEventSubscriber extends AbstractSubcriber {
  @Inject
  public CacheEvictionEventSubscriber(
      BrokerApiWrapper brokerApi,
      CacheEvictionEventRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      @BrokerGson Gson gsonProvider,
      @InstanceId UUID instanceId,
      MessageLogger msgLog,
      SubscriberMetrics subscriberMetrics) {
    super(
        brokerApi,
        eventRouter,
        droppedEventListeners,
        gsonProvider,
        instanceId,
        msgLog,
        subscriberMetrics);
  }

  @Override
  protected EventTopic getTopic() {
    return EventTopic.CACHE_TOPIC;
  }
}
