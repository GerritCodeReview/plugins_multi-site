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

package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.googlesource.gerrit.plugins.multisite.consumer.AbstractSubcriber;
import com.googlesource.gerrit.plugins.multisite.consumer.CacheEvictionEventSubscriber;
import com.googlesource.gerrit.plugins.multisite.consumer.DroppedEventListener;
import com.googlesource.gerrit.plugins.multisite.consumer.IndexEventSubscriber;
import com.googlesource.gerrit.plugins.multisite.consumer.ProjectUpdateEventSubscriber;
import com.googlesource.gerrit.plugins.multisite.consumer.SourceAwareEventWrapper;
import com.googlesource.gerrit.plugins.multisite.consumer.StreamEventSubscriber;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.kafka.KafkaConfiguration;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;

public class KafkaConsumerModule extends LifecycleModule {

  private KafkaConfiguration config;

  @Inject
  public KafkaConsumerModule(KafkaConfiguration config) {
    this.config = config;
  }

  @Override
  protected void configure() {

    bind(new TypeLiteral<Deserializer<byte[]>>() {}).toInstance(new ByteArrayDeserializer());
    bind(new TypeLiteral<Deserializer<SourceAwareEventWrapper>>() {})
        .to(KafkaEventDeserializer.class);

    if (config.kafkaSubscriber().enabledEvent(EventTopic.INDEX_TOPIC)) {
      DynamicSet.bind(binder(), AbstractSubcriber.class).to(IndexEventSubscriber.class);
    }
    if (config.kafkaSubscriber().enabledEvent(EventTopic.STREAM_EVENT_TOPIC)) {
      DynamicSet.bind(binder(), AbstractSubcriber.class).to(StreamEventSubscriber.class);
    }
    if (config.kafkaSubscriber().enabledEvent(EventTopic.CACHE_TOPIC)) {
      DynamicSet.bind(binder(), AbstractSubcriber.class).to(CacheEvictionEventSubscriber.class);
    }
    if (config.kafkaSubscriber().enabledEvent(EventTopic.PROJECT_LIST_TOPIC)) {
      DynamicSet.bind(binder(), AbstractSubcriber.class).to(ProjectUpdateEventSubscriber.class);
    }

    DynamicSet.setOf(binder(), DroppedEventListener.class);
  }
}
