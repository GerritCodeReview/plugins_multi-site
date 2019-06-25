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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;

@Singleton
public class KafkaConsumerFactory {
  private KafkaConfiguration config;

  @Inject
  public KafkaConsumerFactory(KafkaConfiguration configuration) {
    this.config = configuration;
  }

  public Consumer<byte[], byte[]> create(Deserializer<byte[]> keyDeserializer, UUID instanceId) {
    return new KafkaConsumer<>(
        config.kafkaSubscriber().initPropsWith(instanceId),
        keyDeserializer,
        new ByteArrayDeserializer());
  }
}
