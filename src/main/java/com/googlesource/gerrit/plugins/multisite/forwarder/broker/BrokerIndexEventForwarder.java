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
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApiWrapper;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.IndexEvent;

public class BrokerIndexEventForwarder implements IndexEventForwarder {
  private final BrokerApiWrapper broker;
  private final Configuration cfg;

  @Inject
  BrokerIndexEventForwarder(BrokerApiWrapper broker, Configuration cfg) {
    this.broker = broker;
    this.cfg = cfg;
  }

  @Override
  public boolean index(IndexEvent event) {
    return broker.send(EventTopic.INDEX_TOPIC.topic(cfg), event);
  }

  @Override
  public boolean batchIndex(IndexEvent event) {
    return broker.send(EventTopic.BATCH_INDEX_TOPIC.topic(cfg), event);
  }
}
