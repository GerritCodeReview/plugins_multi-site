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

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

@Singleton
public class KafkaEventDeserializer implements Deserializer<BrokerReadEvent> {

  private final StringDeserializer stringDeserializer = new StringDeserializer();
  private Provider<Gson> gsonProvider;

  // To be used when providing this deserializer with class name (then need to add a configuration
  // entry to set the gson.provider
  public KafkaEventDeserializer() {}

  @Inject
  public KafkaEventDeserializer(Provider<Gson> gsonProvider) {
    this.gsonProvider = gsonProvider;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    gsonProvider = (Provider<Gson>) configs.get("gson.provider");
  }

  @Override
  public BrokerReadEvent deserialize(String topic, byte[] data) {
    return gsonProvider
        .get()
        .fromJson(stringDeserializer.deserialize(topic, data), BrokerReadEvent.class);
  }

  @Override
  public void close() {}
}
