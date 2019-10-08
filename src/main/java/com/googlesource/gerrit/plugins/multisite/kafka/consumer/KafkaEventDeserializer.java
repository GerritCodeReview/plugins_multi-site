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

import com.gerritforge.gerrit.eventbroker.SourceAwareEventWrapper;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerGson;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

@Singleton
public class KafkaEventDeserializer implements Deserializer<SourceAwareEventWrapper> {

  private final StringDeserializer stringDeserializer = new StringDeserializer();
  private Gson gson;

  // To be used when providing this deserializer with class name (then need to add a configuration
  // entry to set the gson.provider
  public KafkaEventDeserializer() {}

  @Inject
  public KafkaEventDeserializer(@BrokerGson Gson gson) {
    this.gson = gson;
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {}

  @Override
  public SourceAwareEventWrapper deserialize(String topic, byte[] data) {
    final SourceAwareEventWrapper result =
        gson.fromJson(stringDeserializer.deserialize(topic, data), SourceAwareEventWrapper.class);

    result.validate();

    return result;
  }

  @Override
  public void close() {}
}
