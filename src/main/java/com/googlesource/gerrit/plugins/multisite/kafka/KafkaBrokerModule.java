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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerSession;
import com.googlesource.gerrit.plugins.multisite.broker.kafka.BrokerPublisher;
import com.googlesource.gerrit.plugins.multisite.broker.kafka.KafkaSession;
import com.googlesource.gerrit.plugins.multisite.consumer.AbstractSubcriber;
import com.googlesource.gerrit.plugins.multisite.consumer.CacheEvictionEventSubscriber;
import com.googlesource.gerrit.plugins.multisite.consumer.IndexEventSubscriber;
import com.googlesource.gerrit.plugins.multisite.consumer.ProjectUpdateEventSubscriber;
import com.googlesource.gerrit.plugins.multisite.consumer.SourceAwareEventWrapper;
import com.googlesource.gerrit.plugins.multisite.consumer.StreamEventSubscriber;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEvictionForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.ProjectListUpdateForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.StreamEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.broker.BrokerCacheEvictionForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.broker.BrokerIndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.broker.BrokerProjectListUpdateForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.broker.BrokerStreamEventForwarder;
import com.googlesource.gerrit.plugins.multisite.kafka.consumer.KafkaEventDeserializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;

public class KafkaBrokerModule extends LifecycleModule {
  private final Configuration config;

  @Inject
  public KafkaBrokerModule(Configuration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bind(new TypeLiteral<Deserializer<byte[]>>() {}).toInstance(new ByteArrayDeserializer());
    bind(new TypeLiteral<Deserializer<SourceAwareEventWrapper>>() {})
        .to(KafkaEventDeserializer.class);

    DynamicSet.bind(binder(), AbstractSubcriber.class).to(IndexEventSubscriber.class);
    DynamicSet.bind(binder(), AbstractSubcriber.class).to(StreamEventSubscriber.class);
    DynamicSet.bind(binder(), AbstractSubcriber.class).to(CacheEvictionEventSubscriber.class);
    DynamicSet.bind(binder(), AbstractSubcriber.class).to(ProjectUpdateEventSubscriber.class);

    listener().to(BrokerPublisher.class);
    bind(BrokerSession.class).to(KafkaSession.class);

    DynamicSet.bind(binder(), IndexEventForwarder.class).to(BrokerIndexEventForwarder.class);
    DynamicSet.bind(binder(), CacheEvictionForwarder.class).to(BrokerCacheEvictionForwarder.class);
    DynamicSet.bind(binder(), ProjectListUpdateForwarder.class)
        .to(BrokerProjectListUpdateForwarder.class);
    DynamicSet.bind(binder(), StreamEventForwarder.class).to(BrokerStreamEventForwarder.class);
  }
}
