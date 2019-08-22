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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApi;
import com.googlesource.gerrit.plugins.multisite.broker.kafka.BrokerPublisher;
import com.googlesource.gerrit.plugins.multisite.consumer.SourceAwareEventWrapper;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.kafka.consumer.KafkaEventSubscriber;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class KafkaBrokerApi implements BrokerApi, LifecycleListener {

  private final BrokerPublisher publisher;
  private final Provider<KafkaEventSubscriber> subscriberProvider;
  private List<KafkaEventSubscriber> subscribers;

  @Inject
  public KafkaBrokerApi(
      BrokerPublisher publisher, Provider<KafkaEventSubscriber> subscriberProvider) {
    this.publisher = publisher;
    this.subscriberProvider = subscriberProvider;
    subscribers = new ArrayList<>();
  }

  @Override
  public boolean send(String topic, Event event) {
    return publisher.publish(topic, event);
  }

  @Override
  public void receiveAync(String topic, Consumer<SourceAwareEventWrapper> eventConsumer) {
    KafkaEventSubscriber subscriber = subscriberProvider.get();
    subscribers.add(subscriber);
    subscriber.subscribe(EventTopic.of(topic), eventConsumer);
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    for (KafkaEventSubscriber subscriber : subscribers) {
      subscriber.shutdown();
    }
  }
}
