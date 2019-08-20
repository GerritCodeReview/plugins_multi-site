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

package com.googlesource.gerrit.plugins.multisite.kafka;

import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApi;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerPublisher;
import java.util.function.Consumer;

public class KafkaBrokerApi implements BrokerApi {

  private final BrokerPublisher publisher;

  @Inject
  public KafkaBrokerApi(BrokerPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public boolean send(String topic, Event event) {
    return publisher.publishEventToTopic(topic, event);
  }

  @Override
  public void receiveAync(String topic, Consumer<Event> eventConsumer) {
    throw new NotImplementedException();
  }
}
