// Copyright (C) 2020 The Android Open Source Project
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

import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApiWrapper;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwarderTask;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.MultiSiteEvent;

public abstract class BrokerForwarder {
  private static final CharSequence HIGH_AVAILABILITY_PLUGIN = "/plugins/high-availability/";
  private static final CharSequence HIGH_AVAILABILITY_FORWARDER = "Forwarded-Index-Event";
  private static final CharSequence HIGH_AVAILABILITY_BATCH_FORWARDER =
      "Forwarded-BatchIndex-Event";

  private final BrokerApiWrapper broker;
  private final Configuration cfg;

  protected BrokerForwarder(BrokerApiWrapper broker, Configuration cfg) {
    this.broker = broker;
    this.cfg = cfg;
  }

  protected boolean currentThreadBelongsToHighAvailabilityPlugin(ForwarderTask task) {
    String currentThreadName = task.getCallerThreadName();

    return currentThreadName.contains(HIGH_AVAILABILITY_PLUGIN)
        || currentThreadName.contains(HIGH_AVAILABILITY_FORWARDER)
        || currentThreadName.contains(HIGH_AVAILABILITY_BATCH_FORWARDER);
  }

  protected boolean send(ForwarderTask task, EventTopic eventTopic, MultiSiteEvent event) {
    // Events generated by the high-availability plugin should be
    // discarded. Sending them around would cause infinite loops.
    if (currentThreadBelongsToHighAvailabilityPlugin(task)) {
      return true;
    }

    return broker.send(eventTopic.topic(cfg), event);
  }
}
