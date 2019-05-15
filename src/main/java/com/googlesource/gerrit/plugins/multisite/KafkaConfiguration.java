package com.googlesource.gerrit.plugins.multisite;

import static com.google.common.base.Suppliers.memoize;
import static com.googlesource.gerrit.plugins.multisite.ConfigurationHelper.getString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConfiguration {

  private static final Logger log = LoggerFactory.getLogger(KafkaConfiguration.class);
  public static final String PLUGIN_NAME = "kafka";
  public static final String KAFKA_CONFIG = PLUGIN_NAME + ".config";
  public static final String KAFKA_PROPERTY_PREFIX = "KafkaProp-";
  static final String KAFKA_SECTION = "kafka";
  static final String ENABLE_KEY = "enabled";
  static final String DEFAULT_KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
  static final boolean DEFAULT_ENABLE_PROCESSING = true;
  private static final int DEFAULT_POLLING_INTERVAL_MS = 1000;

  private final Supplier<KafkaSubscriber> subscriber;
  private final Supplier<Kafka> kafka;
  private final Supplier<KafkaPublisher> publisher;

  @Inject
  KafkaConfiguration(SitePaths sitePaths) {
    this(getConfigFile(sitePaths, KAFKA_CONFIG));
  }

  @VisibleForTesting
  public KafkaConfiguration(Config kafkaConfig) {
    Supplier<Config> lazyCfg = ConfigurationHelper.lazyLoad(kafkaConfig);
    kafka = memoize(() -> new Kafka(lazyCfg));
    publisher = memoize(() -> new KafkaPublisher(lazyCfg));
    subscriber = memoize(() -> new KafkaSubscriber(lazyCfg));
  }

  private static FileBasedConfig getConfigFile(SitePaths sitePaths, String configFileName) {
    return new FileBasedConfig(sitePaths.etc_dir.resolve(configFileName).toFile(), FS.DETECTED);
  }

  public Kafka getKafka() {
    return kafka.get();
  }

  public KafkaSubscriber kafkaSubscriber() {
    return subscriber.get();
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

  public KafkaPublisher kafkaPublisher() {
    return publisher.get();
  }

  public static class KafkaPublisher extends Properties {
    private static final long serialVersionUID = 0L;

    public static final String KAFKA_STRING_SERIALIZER = StringSerializer.class.getName();

    public static final String KAFKA_PUBLISHER_SUBSECTION = "publisher";
    public static final boolean DEFAULT_BROKER_ENABLED = false;

    private final boolean enabled;
    private final Map<EventFamily, Boolean> eventsEnabled;

    private KafkaPublisher(Supplier<Config> cfg) {
      enabled =
          cfg.get()
              .getBoolean(
                  KAFKA_SECTION, KAFKA_PUBLISHER_SUBSECTION, ENABLE_KEY, DEFAULT_BROKER_ENABLED);

      eventsEnabled = eventsEnabled(cfg, KAFKA_PUBLISHER_SUBSECTION);

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

    public boolean enabled() {
      return enabled;
    }

    public boolean enabledEvent(EventFamily eventType) {
      return eventsEnabled.get(eventType);
    }
  }

  public class KafkaSubscriber extends Properties {
    private static final long serialVersionUID = 1L;

    static final String KAFKA_SUBSCRIBER_SUBSECTION = "subscriber";

    private final boolean enabled;
    private final Integer pollingInterval;
    private Map<EventFamily, Boolean> eventsEnabled;
    private final Config cfg;

    public KafkaSubscriber(Supplier<Config> configSupplier) {
      this.cfg = configSupplier.get();

      this.pollingInterval =
          cfg.getInt(
              KAFKA_SECTION,
              KAFKA_SUBSCRIBER_SUBSECTION,
              "pollingIntervalMs",
              DEFAULT_POLLING_INTERVAL_MS);

      enabled = cfg.getBoolean(KAFKA_SECTION, KAFKA_SUBSCRIBER_SUBSECTION, ENABLE_KEY, false);

      eventsEnabled = eventsEnabled(configSupplier, KAFKA_SUBSCRIBER_SUBSECTION);

      if (enabled) {
        applyKafkaConfig(configSupplier, KAFKA_SUBSCRIBER_SUBSECTION, this);
      }
    }

    public boolean enabled() {
      return enabled;
    }

    public boolean enabledEvent(EventFamily eventFamily) {
      return eventsEnabled.get(eventFamily);
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

  public static class Kafka {
    private final Map<EventFamily, String> eventTopics;
    private final String bootstrapServers;

    private static final Map<EventFamily, String> EVENT_TOPICS =
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
}
