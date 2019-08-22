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

import com.google.gerrit.server.events.Event;
import com.googlesource.gerrit.plugins.multisite.consumer.SourceAwareEventWrapper;
import java.util.function.Consumer;

/** API for sending/receiving events through a message Broker. */
public interface BrokerApi {

  /**
   * Send an event to a topic.
   *
   * @param topic
   * @param event
   * @return true if the event was successfully sent. False otherwise.
   */
  boolean send(String topic, Event event);

  /**
   * Receive asynchronously events from a topic.
   *
   * @param topic
   * @param eventConsumer
   */
  void receiveAync(String topic, Consumer<SourceAwareEventWrapper> eventConsumer);
}
