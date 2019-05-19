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

package com.googlesource.gerrit.plugins.multisite.broker.kafka;

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;

public class ProducerProvider implements Provider<Producer<String, String>> {

  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private KafkaConfiguration kafkaConfig;

  @Inject
  public ProducerProvider(KafkaConfiguration kafkaConfig) {
    this.kafkaConfig = kafkaConfig;
  }

  @Override
  public Producer<String, String> get() {
    log.atInfo().log("Connect to %s...", kafkaConfig.getKafka().getBootstrapServers());
    return new KafkaProducer<>(kafkaConfig.kafkaPublisher());
  }
}
