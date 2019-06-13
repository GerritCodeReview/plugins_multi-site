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

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.googlesource.gerrit.plugins.multisite.event.subscriber.AbstractSubscriber;
import com.googlesource.gerrit.plugins.multisite.event.subscriber.EventSubscriber;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.MultiSiteEvent;
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
    MultiSiteEvent.registerEventTypes();
    bind(new TypeLiteral<Deserializer<byte[]>>() {}).toInstance(new ByteArrayDeserializer());
    bind(new TypeLiteral<Deserializer<SourceAwareEventWrapper>>() {})
        .to(KafkaEventDeserializer.class);

    DynamicItem.bind(binder(), EventSubscriber.class).to(KafkaEventSubscriber.class);

    if (config.kafkaSubscriber().enabledEvent(EventFamily.INDEX_EVENT)) {
      DynamicSet.bind(binder(), AbstractSubscriber.class).to(IndexEventSubscriber.class);
    }
    if (config.kafkaSubscriber().enabledEvent(EventFamily.STREAM_EVENT)) {
      DynamicSet.bind(binder(), AbstractSubscriber.class).to(StreamEventSubscriber.class);
    }
    if (config.kafkaSubscriber().enabledEvent(EventFamily.CACHE_EVENT)) {
      DynamicSet.bind(binder(), AbstractSubscriber.class)
          .to(KafkaCacheEvictionEventSubscriber.class);
    }
    if (config.kafkaSubscriber().enabledEvent(EventFamily.PROJECT_LIST_EVENT)) {
      DynamicSet.bind(binder(), AbstractSubscriber.class).to(ProjectUpdateEventSubscriber.class);
    }

    DynamicSet.setOf(binder(), DroppedEventListener.class);
  }
}
