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
import com.google.inject.TypeLiteral;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration.KafkaSubscriber;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.MultiSiteEvent;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;

public class KafkaConsumerModule extends LifecycleModule {

  private final KafkaSubscriber kafkaSubscriber;

  public KafkaConsumerModule(KafkaSubscriber kafkaSubscriber) {
    this.kafkaSubscriber = kafkaSubscriber;
  }

  @Override
  protected void configure() {
    MultiSiteEvent.registerEventTypes();
    bind(new TypeLiteral<Deserializer<byte[]>>() {}).toInstance(new ByteArrayDeserializer());
    bind(new TypeLiteral<Deserializer<SourceAwareEventWrapper>>() {})
        .to(KafkaEventDeserializer.class);

    bind(Executor.class)
        .annotatedWith(ConsumerExecutor.class)
        .toInstance(Executors.newFixedThreadPool(EventFamily.values().length));
    listener().to(MultiSiteKafkaConsumerRunner.class);

    DynamicSet.setOf(binder(), AbstractKafkaSubcriber.class);

    if (kafkaSubscriber.enabledEvent(EventFamily.INDEX_EVENT)) {
      DynamicSet.bind(binder(), AbstractKafkaSubcriber.class).to(IndexEventSubscriber.class);
    }
    if (kafkaSubscriber.enabledEvent(EventFamily.STREAM_EVENT)) {
      DynamicSet.bind(binder(), AbstractKafkaSubcriber.class).to(StreamEventSubscriber.class);
    }
    if (kafkaSubscriber.enabledEvent(EventFamily.CACHE_EVENT)) {
      DynamicSet.bind(binder(), AbstractKafkaSubcriber.class)
          .to(CacheEvictionEventSubscriber.class);
    }
    if (kafkaSubscriber.enabledEvent(EventFamily.PROJECT_LIST_EVENT)) {
      DynamicSet.bind(binder(), AbstractKafkaSubcriber.class)
          .to(ProjectUpdateEventSubscriber.class);
    }

    DynamicSet.setOf(binder(), DroppedEventListener.class);
  }
}
