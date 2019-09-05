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

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class KafkaConfiguration {

  private static final Logger log = LoggerFactory.getLogger(KafkaConfiguration.class);
  static final String KAFKA_SECTION = "kafka";
  private static final String DEFAULT_KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
  private static final int DEFAULT_POLLING_INTERVAL_MS = 1000;

  private final Supplier<KafkaSubscriber> subscriber;
  private final Supplier<Kafka> kafka;
  private final Supplier<KafkaPublisher> publisher;

  @Inject
  public KafkaConfiguration(PluginConfigFactory configFactory, @PluginName String pluginName) {
    Config cfg = configFactory.getGlobalPluginConfig(pluginName);
    kafka = memoize(() -> new Kafka(cfg));
    publisher = memoize(() -> new KafkaPublisher(cfg));
    subscriber = memoize(() -> new KafkaSubscriber(cfg));
  }

  public Kafka getKafka() {
    return kafka.get();
  }

  public KafkaSubscriber kafkaSubscriber() {
    return subscriber.get();
  }

  private static void applyKafkaConfig(Config config, String subsectionName, Properties target) {
    for (String section : config.getSubsections(KAFKA_SECTION)) {
      if (section.equals(subsectionName)) {
        for (String name : config.getNames(KAFKA_SECTION, section, true)) {
          Object value = config.getString(KAFKA_SECTION, subsectionName, name);
          String propName =
              CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, name).replaceAll("-", ".");
          log.info("[{}] Setting kafka property: {} = {}", subsectionName, propName, value);
          target.put(propName, value);
        }
      }
    }
    target.put(
        "bootstrap.servers",
        getString(
            config, KAFKA_SECTION, null, "bootstrapServers", DEFAULT_KAFKA_BOOTSTRAP_SERVERS));
  }

  private static String getString(
      Config cfg, String section, String subsection, String name, String defaultValue) {
    String value = cfg.getString(section, subsection, name);
    if (!Strings.isNullOrEmpty(value)) {
      return value;
    }
    return defaultValue;
  }

  public KafkaPublisher kafkaPublisher() {
    return publisher.get();
  }

  public static class Kafka {
    private final Map<EventTopic, String> eventTopics;
    private final String bootstrapServers;

    Kafka(Config config) {
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
        Config cfg, String section, String subsection, String name, String defaultValue) {
      String value = cfg.getString(section, subsection, name);
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

    private KafkaPublisher(Config kafkaConfig) {
      setDefaults();
      applyKafkaConfig(kafkaConfig, KAFKA_PUBLISHER_SUBSECTION, this);
    }

    private void setDefaults() {
      put("acks", "all");
      put("retries", 10);
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

    public KafkaSubscriber(Config kafkaCfg) {
      this.cfg = kafkaCfg;

      this.pollingInterval =
          cfg.getInt(
              KAFKA_SECTION,
              KAFKA_SUBSCRIBER_SUBSECTION,
              "pollingIntervalMs",
              DEFAULT_POLLING_INTERVAL_MS);

      applyKafkaConfig(kafkaCfg, KAFKA_SUBSCRIBER_SUBSECTION, this);
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
