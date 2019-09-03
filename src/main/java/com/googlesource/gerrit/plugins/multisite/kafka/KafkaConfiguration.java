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

import static com.google.common.base.Suppliers.memoize;
import static com.google.common.base.Suppliers.ofInstance;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class KafkaConfiguration {

  private static final Logger log = LoggerFactory.getLogger(KafkaConfiguration.class);
  static final String KAFKA_PROPERTY_PREFIX = "KafkaProp-";
  static final String KAFKA_SECTION = "kafka";
  private static final String KAFKA_CONFIG = "kafka.config";
  private static final String DEFAULT_KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
  private static final int DEFAULT_POLLING_INTERVAL_MS = 1000;

  private final Supplier<KafkaSubscriber> subscriber;
  private final Supplier<Kafka> kafka;
  private final Supplier<KafkaPublisher> publisher;
  private Supplier<Config> lazyCfg;

  @Inject
  public KafkaConfiguration(SitePaths sitePaths, Configuration configuration) {
    this(getConfigFile(sitePaths, KAFKA_CONFIG), configuration);
  }

  @VisibleForTesting
  public KafkaConfiguration(Config kafkaConfig, Configuration multiSiteConfig) {
    lazyCfg = lazyLoad(kafkaConfig);
    kafka = memoize(() -> new Kafka(lazyCfg));
    publisher = memoize(() -> new KafkaPublisher(multiSiteConfig, lazyCfg));
    subscriber = memoize(() -> new KafkaSubscriber(multiSiteConfig, lazyCfg));
  }

  public Kafka getKafka() {
    return kafka.get();
  }

  public KafkaSubscriber kafkaSubscriber() {
    return subscriber.get();
  }

  private static FileBasedConfig getConfigFile(SitePaths sitePaths, String configFileName) {
    return new FileBasedConfig(sitePaths.etc_dir.resolve(configFileName).toFile(), FS.DETECTED);
  }

  private static void applyKafkaConfig(
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

  public static class Kafka {
    private final Map<EventTopic, String> eventTopics;
    private final String bootstrapServers;

    Kafka(Supplier<Config> config) {
      this.bootstrapServers =
          getString(
              config, KAFKA_SECTION, null, "bootstrapServers", DEFAULT_KAFKA_BOOTSTRAP_SERVERS);

      this.eventTopics = new HashMap<>();
      for (EventTopic eventTopic : EventTopic.values()) {
        eventTopics.put(
            eventTopic,
            getString(config, KAFKA_SECTION, null, eventTopic.topicAliasKey(), eventTopic.topic()));
      }
    }

    public String getTopicAlias(EventTopic topic) {
      return eventTopics.get(topic);
    }

    public String getBootstrapServers() {
      return bootstrapServers;
    }

    private static String getString(
        Supplier<Config> cfg, String section, String subsection, String name, String defaultValue) {
      String value = cfg.get().getString(section, subsection, name);
      if (!Strings.isNullOrEmpty(value)) {
        return value;
      }
      return defaultValue;
    }
  }

  public static class KafkaPublisher extends Properties {
    private static final long serialVersionUID = 0L;

    public static final String KAFKA_STRING_SERIALIZER = StringSerializer.class.getName();
    public static final String KAFKA_PUBLISHER_SUBSECTION = "publisher";

    private KafkaPublisher(Configuration configuration, Supplier<Config> kafkaConfig) {
      if (configuration.getBrokerPublisher().enabled()) {
        setDefaults();
        applyKafkaConfig(kafkaConfig, KAFKA_PUBLISHER_SUBSECTION, this);
      }
    }

    private void setDefaults() {
      put("acks", "all");
      put("retries", 0);
      put("batch.size", 16384);
      put("linger.ms", 1);
      put("buffer.memory", 33554432);
      put("key.serializer", KAFKA_STRING_SERIALIZER);
      put("value.serializer", KAFKA_STRING_SERIALIZER);
      put("reconnect.backoff.ms", 5000L);
    }
  }

  public static class KafkaSubscriber extends Properties {
    private static final long serialVersionUID = 1L;

    static final String KAFKA_SUBSCRIBER_SUBSECTION = "subscriber";

    private final Integer pollingInterval;
    private final Config cfg;

    public KafkaSubscriber(Configuration configuration, Supplier<Config> kafkaCfg) {
      this.cfg = kafkaCfg.get();

      this.pollingInterval =
          cfg.getInt(
              KAFKA_SECTION,
              KAFKA_SUBSCRIBER_SUBSECTION,
              "pollingIntervalMs",
              DEFAULT_POLLING_INTERVAL_MS);

      if (configuration.getBrokerSubscriber().enabled()) {
        applyKafkaConfig(kafkaCfg, KAFKA_SUBSCRIBER_SUBSECTION, this);
      }
    }

    public Properties initPropsWith(UUID instanceId) {
      String groupId =
          getString(
              cfg, KAFKA_SECTION, KAFKA_SUBSCRIBER_SUBSECTION, "groupId", instanceId.toString());
      this.put("group.id", groupId);

      return this;
    }

    public Integer getPollingInterval() {
      return pollingInterval;
    }

    private String getString(
        Config cfg, String section, String subsection, String name, String defaultValue) {
      String value = cfg.getString(section, subsection, name);
      if (!Strings.isNullOrEmpty(value)) {
        return value;
      }
      return defaultValue;
    }
  }
}
