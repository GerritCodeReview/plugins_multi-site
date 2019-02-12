package com.googlesource.gerrit.plugins.multisite;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Properties;
import org.eclipse.jgit.lib.Config;

@Singleton
public class KafkaConsumerConfiguration {
  static final String KAFKA_SECTION = "kafka";
  static final String KAFKA_CONSUMER_SUBSECTION = "consumer";

  private final String topic;
  private final Integer pollingInterval;
  private final Properties props = new Properties();

  @Inject
  public KafkaConsumerConfiguration(
      PluginConfigFactory pluginConfigFactory, @PluginName String pluginName) {

    Config cfg = pluginConfigFactory.getGlobalPluginConfig(pluginName);

    this.topic = cfg.getString(KAFKA_SECTION, null, "eventTopic");
    this.pollingInterval =
        cfg.getInt(KAFKA_SECTION, KAFKA_CONSUMER_SUBSECTION, "pollingIntervalMs", 1000);

    String server = getString(cfg, KAFKA_SECTION, null, "bootstrapServers", "localhost:9092");
    String groupId = getString(cfg, KAFKA_SECTION, KAFKA_CONSUMER_SUBSECTION, "groupId", "groupA");
    Boolean autoCommit =
        cfg.getBoolean(KAFKA_SECTION, KAFKA_CONSUMER_SUBSECTION, "autoCommit", true);
    Integer autoCommitInterval =
        cfg.getInt(KAFKA_SECTION, KAFKA_CONSUMER_SUBSECTION, "autoCommitIntervalMs", 1000);

    props.put("bootstrap.servers", server);
    props.put("group.id", groupId);
    props.put("enable.auto.commit", autoCommit);
    props.put("auto.commit.interval.ms", autoCommitInterval);
  }

  public String getTopic() {
    return topic;
  }

  public Properties getProps() {
    return props;
  }

  public Integer getPollingInterval() {
    return pollingInterval;
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
