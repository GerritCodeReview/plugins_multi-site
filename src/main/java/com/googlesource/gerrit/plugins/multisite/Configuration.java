// Copyright (C) 2015 The Android Open Source Project
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.spi.Message;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement.EnforcePolicy;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
public class Configuration {
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  public static final String PLUGIN_NAME = "multi-site";
  public static final String MULTI_SITE_CONFIG = PLUGIN_NAME + ".config";
  public static final String REPLICATION_CONFIG = "replication.config";

  static final String INSTANCE_ID_FILE = "instanceId.data";

  // common parameters to cache and index sections
  static final String THREAD_POOL_SIZE_KEY = "threadPoolSize";
  static final int DEFAULT_INDEX_MAX_TRIES = 2;
  static final int DEFAULT_INDEX_RETRY_INTERVAL = 30000;
  private static final int DEFAULT_POLLING_INTERVAL_MS = 1000;
  static final int DEFAULT_THREAD_POOL_SIZE = 4;
  static final String NUM_STRIPED_LOCKS = "numStripedLocks";
  static final int DEFAULT_NUM_STRIPED_LOCKS = 10;
  static final String ENABLE_KEY = "enabled";
  static final String DEFAULT_KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
  static final boolean DEFAULT_ENABLE_PROCESSING = true;
  static final String KAFKA_SECTION = "kafka";
  public static final String KAFKA_PROPERTY_PREFIX = "KafkaProp-";
  static final String KAFKA_PUBLISHER_SUBSECTION = "publisher";
  public static final boolean DEFAULT_BROKER_ENABLED = false;

  static final String KAFKA_SUBSCRIBER_SUBSECTION = "subscriber";

  private final Supplier<KafkaPublisher> publisher;
  private final Supplier<Cache> cache;
  private final Supplier<Event> event;
  private final Supplier<Index> index;
  private final Supplier<KafkaSubscriber> subscriber;
  private final Supplier<Kafka> kafka;
  private final Supplier<SharedRefDatabase> sharedRefDb;
  private final Supplier<Collection<Message>> replicationConfigValidation;
  private final Config multiSiteConfig;
  private final Map<EventFamily, Boolean> publisherEventsEnabled;
  private final Boolean publisherEnabled;
  private final Map<EventFamily, Boolean> subscriberEventsEnabled;
  private final Boolean subscriberEnabled;

  @Inject
  Configuration(SitePaths sitePaths) {
    this(getConfigFile(sitePaths, MULTI_SITE_CONFIG), getConfigFile(sitePaths, REPLICATION_CONFIG));
  }

  @VisibleForTesting
  public Configuration(Config multiSiteConfig, Config replicationConfig) {
    Supplier<Config> lazyMultiSiteCfg = lazyLoad(multiSiteConfig);
    this.multiSiteConfig = multiSiteConfig;
    replicationConfigValidation = lazyValidateReplicatioConfig(replicationConfig);
    kafka = memoize(() -> new Kafka(lazyMultiSiteCfg));
    publisher = memoize(() -> new KafkaPublisher(lazyMultiSiteCfg));
    subscriber = memoize(() -> new KafkaSubscriber(lazyMultiSiteCfg));
    cache = memoize(() -> new Cache(lazyMultiSiteCfg));
    event = memoize(() -> new Event(lazyMultiSiteCfg));
    index = memoize(() -> new Index(lazyMultiSiteCfg));
    sharedRefDb = memoize(() -> new SharedRefDatabase(lazyMultiSiteCfg));
    publisherEventsEnabled = eventsEnabled(lazyMultiSiteCfg, KAFKA_PUBLISHER_SUBSECTION);
    publisherEnabled = enabled(lazyMultiSiteCfg, KAFKA_PUBLISHER_SUBSECTION);
    subscriberEventsEnabled = eventsEnabled(lazyMultiSiteCfg, KAFKA_SUBSCRIBER_SUBSECTION);
    subscriberEnabled = enabled(lazyMultiSiteCfg, KAFKA_SUBSCRIBER_SUBSECTION);
  }

  public Config getMultiSiteConfig() {
    return multiSiteConfig;
  }

  public SharedRefDatabase getSharedRefDb() {
    return sharedRefDb.get();
  }

  public Kafka getKafka() {
    return kafka.get();
  }

  public KafkaPublisher kafkaPublisher() {
    return publisher.get();
  }

  public Cache cache() {
    return cache.get();
  }

  public Event event() {
    return event.get();
  }

  public Index index() {
    return index.get();
  }

  public KafkaSubscriber kafkaSubscriber() {
    return subscriber.get();
  }

  public Collection<Message> validate() {
    return replicationConfigValidation.get();
  }

  public boolean publisherEnabled() {
    return publisherEnabled;
  }

  public boolean publisherEnabledEvent(EventFamily eventType) {
    return publisherEventsEnabled.get(eventType);
  }

  public boolean subscriberEnabled() {
    return subscriberEnabled;
  }

  public boolean subscriberEnabledEvent(EventFamily eventFamily) {
    return subscriberEventsEnabled.get(eventFamily);
  }

  private static FileBasedConfig getConfigFile(SitePaths sitePaths, String configFileName) {
    return new FileBasedConfig(sitePaths.etc_dir.resolve(configFileName).toFile(), FS.DETECTED);
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

  private Supplier<Collection<Message>> lazyValidateReplicatioConfig(Config replicationConfig) {
    if (replicationConfig instanceof FileBasedConfig) {
      FileBasedConfig fileConfig = (FileBasedConfig) replicationConfig;
      try {
        fileConfig.load();
        return memoize(() -> validateReplicationConfig(replicationConfig));
      } catch (IOException | ConfigInvalidException e) {
        return ofInstance(Arrays.asList(new Message("Unable to load replication.config", e)));
      }
    }
    return ofInstance(validateReplicationConfig(replicationConfig));
  }

  private Collection<Message> validateReplicationConfig(Config replicationConfig) {
    if (replicationConfig.getBoolean("gerrit", "replicateOnStartup", false)) {
      return Arrays.asList(
          new Message(
              "Invalid replication.config: gerrit.replicateOnStartup has to be set to 'false' for multi-site setups"));
    }
    return Collections.emptyList();
  }

  private static int getInt(
      Supplier<Config> cfg, String section, String subSection, String name, int defaultValue) {
    try {
      return cfg.get().getInt(section, subSection, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error("invalid value for {}; using default value {}", name, defaultValue);
      log.debug("Failed to retrieve integer value: {}", e.getMessage(), e);
      return defaultValue;
    }
  }

  private static String getString(
      Supplier<Config> cfg, String section, String subsection, String name, String defaultValue) {
    String value = cfg.get().getString(section, subsection, name);
    if (!Strings.isNullOrEmpty(value)) {
      return value;
    }
    return defaultValue;
  }

  private static Boolean enabled(Supplier<Config> config, String section) {
    return config.get().getBoolean(KAFKA_SECTION, section, ENABLE_KEY, DEFAULT_BROKER_ENABLED);
  }

  private static Map<EventFamily, Boolean> eventsEnabled(
      Supplier<Config> config, String subsection) {
    Map<EventFamily, Boolean> eventsEnabled = new HashMap<>();
    for (EventFamily eventFamily : EventFamily.values()) {
      String enabledConfigKey = eventFamily.lowerCamelName() + "Enabled";

      eventsEnabled.put(
          eventFamily,
          config
              .get()
              .getBoolean(KAFKA_SECTION, subsection, enabledConfigKey, DEFAULT_ENABLE_PROCESSING));
    }
    return eventsEnabled;
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

  public static class Kafka {
    private final Map<EventFamily, String> eventTopics;
    private final String bootstrapServers;

    private static final ImmutableMap<EventFamily, String> EVENT_TOPICS =
        ImmutableMap.of(
            EventFamily.INDEX_EVENT,
            "GERRIT.EVENT.INDEX",
            EventFamily.STREAM_EVENT,
            "GERRIT.EVENT.STREAM",
            EventFamily.CACHE_EVENT,
            "GERRIT.EVENT.CACHE",
            EventFamily.PROJECT_LIST_EVENT,
            "GERRIT.EVENT.PROJECT.LIST");

    Kafka(Supplier<Config> config) {
      this.bootstrapServers =
          getString(
              config, KAFKA_SECTION, null, "bootstrapServers", DEFAULT_KAFKA_BOOTSTRAP_SERVERS);

      this.eventTopics = new HashMap<>();
      for (Map.Entry<EventFamily, String> topicDefault : EVENT_TOPICS.entrySet()) {
        String topicConfigKey = topicDefault.getKey().lowerCamelName() + "Topic";
        eventTopics.put(
            topicDefault.getKey(),
            getString(config, KAFKA_SECTION, null, topicConfigKey, topicDefault.getValue()));
      }
    }

    public String getTopic(EventFamily eventType) {
      return eventTopics.get(eventType);
    }

    public String getBootstrapServers() {
      return bootstrapServers;
    }
  }

  public static class KafkaPublisher extends Properties {
    private static final long serialVersionUID = 0L;

    public static final String KAFKA_STRING_SERIALIZER = StringSerializer.class.getName();

    private KafkaPublisher(Supplier<Config> cfg) {

      boolean enabled = enabled(cfg, KAFKA_PUBLISHER_SUBSECTION);
      if (enabled) {
        setDefaults();
        applyKafkaConfig(cfg, KAFKA_PUBLISHER_SUBSECTION, this);
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

    private final Integer pollingInterval;
    private final Config cfg;

    public KafkaSubscriber(Supplier<Config> configSupplier) {
      this.cfg = configSupplier.get();

      this.pollingInterval =
          cfg.getInt(
              KAFKA_SECTION,
              KAFKA_SUBSCRIBER_SUBSECTION,
              "pollingIntervalMs",
              DEFAULT_POLLING_INTERVAL_MS);

      Boolean enabled = enabled(configSupplier, KAFKA_SUBSCRIBER_SUBSECTION);
      if (enabled) {
        applyKafkaConfig(configSupplier, KAFKA_SUBSCRIBER_SUBSECTION, this);
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

  public static class SharedRefDatabase {
    public static final String SECTION = "ref-database";
    public static final String SUBSECTION_ENFORCEMENT_RULES = "enforcementRules";

    private final boolean enabled;
    private final Multimap<EnforcePolicy, String> enforcementRules;

    private SharedRefDatabase(Supplier<Config> cfg) {
      enabled = getBoolean(cfg, SECTION, null, ENABLE_KEY, true);

      enforcementRules = MultimapBuilder.hashKeys().arrayListValues().build();
      for (EnforcePolicy policy : EnforcePolicy.values()) {
        enforcementRules.putAll(
            policy, getList(cfg, SECTION, SUBSECTION_ENFORCEMENT_RULES, policy.name()));
      }
    }

    public boolean isEnabled() {
      return enabled;
    }

    public Multimap<EnforcePolicy, String> getEnforcementRules() {
      return enforcementRules;
    }

    private List<String> getList(
        Supplier<Config> cfg, String section, String subsection, String name) {
      return ImmutableList.copyOf(cfg.get().getStringList(section, subsection, name));
    }
  }

  /** Common parameters to cache, event, index */
  public abstract static class Forwarding {
    static final boolean DEFAULT_SYNCHRONIZE = true;
    static final String SYNCHRONIZE_KEY = "synchronize";

    private final boolean synchronize;

    private Forwarding(Supplier<Config> cfg, String section) {
      synchronize =
          Configuration.getBoolean(cfg, section, null, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE);
    }

    public boolean synchronize() {
      return synchronize;
    }
  }

  public static class Cache extends Forwarding {
    static final String CACHE_SECTION = "cache";
    static final String PATTERN_KEY = "pattern";

    private final int threadPoolSize;
    private final List<String> patterns;

    private Cache(Supplier<Config> cfg) {
      super(cfg, CACHE_SECTION);
      threadPoolSize =
          getInt(cfg, CACHE_SECTION, null, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
      patterns = Arrays.asList(cfg.get().getStringList(CACHE_SECTION, null, PATTERN_KEY));
    }

    public int threadPoolSize() {
      return threadPoolSize;
    }

    public List<String> patterns() {
      return Collections.unmodifiableList(patterns);
    }
  }

  public static class Event extends Forwarding {
    static final String EVENT_SECTION = "event";

    private Event(Supplier<Config> cfg) {
      super(cfg, EVENT_SECTION);
    }
  }

  public static class Index extends Forwarding {
    static final String INDEX_SECTION = "index";
    static final String MAX_TRIES_KEY = "maxTries";
    static final String RETRY_INTERVAL_KEY = "retryInterval";

    private final int threadPoolSize;
    private final int retryInterval;
    private final int maxTries;

    private final int numStripedLocks;

    private Index(Supplier<Config> cfg) {
      super(cfg, INDEX_SECTION);
      threadPoolSize =
          getInt(cfg, INDEX_SECTION, null, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
      retryInterval =
          getInt(cfg, INDEX_SECTION, null, RETRY_INTERVAL_KEY, DEFAULT_INDEX_RETRY_INTERVAL);
      maxTries = getInt(cfg, INDEX_SECTION, null, MAX_TRIES_KEY, DEFAULT_INDEX_MAX_TRIES);
      numStripedLocks =
          getInt(cfg, INDEX_SECTION, null, NUM_STRIPED_LOCKS, DEFAULT_NUM_STRIPED_LOCKS);
    }

    public int threadPoolSize() {
      return threadPoolSize;
    }

    public int retryInterval() {
      return retryInterval;
    }

    public int maxTries() {
      return maxTries;
    }

    public int numStripedLocks() {
      return numStripedLocks;
    }
  }

  static boolean getBoolean(
      Supplier<Config> cfg, String section, String subsection, String name, boolean defaultValue) {
    try {
      return cfg.get().getBoolean(section, subsection, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error("invalid value for {}; using default value {}", name, defaultValue);
      log.debug("Failed to retrieve boolean value: {}", e.getMessage(), e);
      return defaultValue;
    }
  }
}
