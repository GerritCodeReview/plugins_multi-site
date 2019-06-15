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

package com.googlesource.gerrit.plugins.multisite;

import static com.google.common.base.Suppliers.memoize;
import static com.google.common.base.Suppliers.ofInstance;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.googlesource.gerrit.plugins.multisite.Configuration.Kafka;
import com.googlesource.gerrit.plugins.multisite.Configuration.KafkaPublisher;
import com.googlesource.gerrit.plugins.multisite.Configuration.KafkaSubscriber;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConfiguration {
  private static final Logger log = LoggerFactory.getLogger(KafkaConfiguration.class);

  static final String KAFKA_PROPERTY_PREFIX = "KafkaProp-";
  static final String KAFKA_SECTION = "kafka";
  static final String ENABLE_KEY = "enabled";
  static final String DEFAULT_KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
  static final boolean DEFAULT_ENABLE_PROCESSING = true;
  static final int DEFAULT_POLLING_INTERVAL_MS = 1000;

  private final Supplier<KafkaSubscriber> subscriber;
  private final Supplier<Kafka> kafka;
  private final Supplier<KafkaPublisher> publisher;

  @VisibleForTesting
  public KafkaConfiguration(Config kafkaConfig) {
    Supplier<Config> lazyCfg = lazyLoad(kafkaConfig);
    kafka = memoize(() -> new Kafka(lazyCfg));
    publisher = memoize(() -> new KafkaPublisher(lazyCfg));
    subscriber = memoize(() -> new KafkaSubscriber(lazyCfg));
  }

  public Kafka getKafka() {
    return kafka.get();
  }

  public KafkaSubscriber kafkaSubscriber() {
    return subscriber.get();
  }

  static void applyKafkaConfig(
      Supplier<Config> configSupplier, String subsectionName, Properties target) {
    Config config = configSupplier.get();
    for (String section : config.getSubsections(KAFKA_SECTION)) {
      if (section.equals(subsectionName)) {
        for (String name : config.getNames(KAFKA_SECTION, section, true)) {
          if (name.startsWith(KAFKA_PROPERTY_PREFIX)) {
            Object value = config.getString(KAFKA_SECTION, subsectionName, name);
            String configProperty = name.replaceFirst(KAFKA_PROPERTY_PREFIX, "");
            String propName =
                CaseFormat.LOWER_CAMEL
                    .to(CaseFormat.LOWER_HYPHEN, configProperty)
                    .replaceAll("-", ".");
            log.info("[{}] Setting kafka property: {} = {}", subsectionName, propName, value);
            target.put(propName, value);
          }
        }
      }
    }
    target.put(
        "bootstrap.servers",
        getString(
            configSupplier,
            KAFKA_SECTION,
            null,
            "bootstrapServers",
            DEFAULT_KAFKA_BOOTSTRAP_SERVERS));
  }

  private static String getString(
      Supplier<Config> cfg, String section, String subsection, String name, String defaultValue) {
    String value = cfg.get().getString(section, subsection, name);
    if (!Strings.isNullOrEmpty(value)) {
      return value;
    }
    return defaultValue;
  }

  static Map<EventFamily, Boolean> eventsEnabled(Supplier<Config> config, String subsection) {
    Map<EventFamily, Boolean> eventsEnabled = new HashMap<>();
    for (EventFamily eventFamily : EventFamily.values()) {
      String enabledConfigKey = eventFamily.lowerCamelName() + "Enabled";

      eventsEnabled.put(
          eventFamily,
          config
              .get()
              .getBoolean(
                  KafkaConfiguration.KAFKA_SECTION,
                  subsection,
                  enabledConfigKey,
                  KafkaConfiguration.DEFAULT_ENABLE_PROCESSING));
    }
    return eventsEnabled;
  }

  public KafkaPublisher kafkaPublisher() {
    return publisher.get();
  }

  private Supplier<Config> lazyLoad(Config config) {
    if (config instanceof FileBasedConfig) {
      return memoize(
          () -> {
            FileBasedConfig fileConfig = (FileBasedConfig) config;
            String fileConfigFileName = fileConfig.getFile().getPath();
            try {
              log.info("Loading configuration from {}", fileConfigFileName);
              fileConfig.load();
            } catch (IOException | ConfigInvalidException e) {
              log.error("Unable to load configuration from " + fileConfigFileName, e);
            }
            return fileConfig;
          });
    }
    return ofInstance(config);
  }
}
