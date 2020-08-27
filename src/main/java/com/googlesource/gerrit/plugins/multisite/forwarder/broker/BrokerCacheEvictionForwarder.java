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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApi;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApiWrapper;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEvictionForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwarderTask;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;

@Singleton
<<<<<<< HEAD   (9a4a33 Honour index retries when indexing groups)
public class BrokerCacheEvictionForwarder implements CacheEvictionForwarder {
  private final BrokerApi broker;
=======
public class BrokerCacheEvictionForwarder extends BrokerForwarder
    implements CacheEvictionForwarder {
>>>>>>> CHANGE (673eda Do not forward events from high-availability)

  @Inject
<<<<<<< HEAD   (9a4a33 Honour index retries when indexing groups)
  BrokerCacheEvictionForwarder(BrokerApiWrapper broker) {
    this.broker = broker;
=======
  BrokerCacheEvictionForwarder(BrokerApiWrapper broker, Configuration cfg) {
    super(broker, cfg);
>>>>>>> CHANGE (673eda Do not forward events from high-availability)
  }

  @Override
<<<<<<< HEAD   (9a4a33 Honour index retries when indexing groups)
  public boolean evict(CacheEvictionEvent event) {
    return broker.send(EventTopic.CACHE_TOPIC.topic(), event);
=======
  public boolean evict(ForwarderTask task, CacheEvictionEvent event) {
    return send(task, EventTopic.CACHE_TOPIC, event);
>>>>>>> CHANGE (673eda Do not forward events from high-availability)
  }
}
