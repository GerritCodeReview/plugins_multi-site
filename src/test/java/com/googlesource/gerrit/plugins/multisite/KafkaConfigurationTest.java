package com.googlesource.gerrit.plugins.multisite;

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.multisite.Configuration.ENABLE_KEY;
import static com.googlesource.gerrit.plugins.multisite.KafkaConfiguration.KAFKA_PROPERTY_PREFIX;
import static com.googlesource.gerrit.plugins.multisite.KafkaConfiguration.KAFKA_SECTION;
import static com.googlesource.gerrit.plugins.multisite.KafkaConfiguration.KafkaPublisher.KAFKA_PUBLISHER_SUBSECTION;
import static com.googlesource.gerrit.plugins.multisite.KafkaConfiguration.KafkaSubscriber.KAFKA_SUBSCRIBER_SUBSECTION;

import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KafkaConfigurationTest {

  private Config globalPluginConfig;

  @Before
  public void setUp() {
    globalPluginConfig = new Config();
  }

  private KafkaConfiguration getConfiguration() {
    return new KafkaConfiguration(globalPluginConfig);
  }

  @Test
  public void kafkaSubscriberPropertiesAreSetWhenSectionIsEnabled() {
    final String kafkaPropertyName = KAFKA_PROPERTY_PREFIX + "fooBarBaz";
    final String kafkaPropertyValue = "aValue";
    globalPluginConfig.setBoolean(KAFKA_SECTION, KAFKA_SUBSCRIBER_SUBSECTION, ENABLE_KEY, true);
    globalPluginConfig.setString(
        KAFKA_SECTION, KAFKA_SUBSCRIBER_SUBSECTION, kafkaPropertyName, kafkaPropertyValue);

    final String property = getConfiguration().kafkaSubscriber().getProperty("foo.bar.baz");

    assertThat(property.equals(kafkaPropertyValue)).isTrue();
  }

  @Test
  public void kafkaSubscriberPropertiesAreNotSetWhenSectionIsDisabled() {
    final String kafkaPropertyName = KAFKA_PROPERTY_PREFIX + "fooBarBaz";
    final String kafkaPropertyValue = "aValue";
    globalPluginConfig.setBoolean(KAFKA_SECTION, KAFKA_SUBSCRIBER_SUBSECTION, ENABLE_KEY, false);
    globalPluginConfig.setString(
        KAFKA_SECTION, KAFKA_SUBSCRIBER_SUBSECTION, kafkaPropertyName, kafkaPropertyValue);

    final String property = getConfiguration().kafkaSubscriber().getProperty("foo.bar.baz");

    assertThat(property).isNull();
  }

  @Test
  public void kafkaSubscriberPropertiesAreIgnoredWhenPrefixIsNotSet() {
    final String kafkaPropertyName = "fooBarBaz";
    final String kafkaPropertyValue = "aValue";
    globalPluginConfig.setBoolean(KAFKA_SECTION, KAFKA_SUBSCRIBER_SUBSECTION, ENABLE_KEY, true);
    globalPluginConfig.setString(
        KAFKA_SECTION, KAFKA_SUBSCRIBER_SUBSECTION, kafkaPropertyName, kafkaPropertyValue);

    final String property = getConfiguration().kafkaSubscriber().getProperty("foo.bar.baz");

    assertThat(property).isNull();
  }

  @Test
  public void kafkaPublisherPropertiesAreSetWhenSectionIsEnabled() {
    final String kafkaPropertyName = KAFKA_PROPERTY_PREFIX + "fooBarBaz";
    final String kafkaPropertyValue = "aValue";
    globalPluginConfig.setBoolean(KAFKA_SECTION, KAFKA_PUBLISHER_SUBSECTION, ENABLE_KEY, true);
    globalPluginConfig.setString(
        KAFKA_SECTION, KAFKA_PUBLISHER_SUBSECTION, kafkaPropertyName, kafkaPropertyValue);

    final String property = getConfiguration().kafkaPublisher().getProperty("foo.bar.baz");

    assertThat(property.equals(kafkaPropertyValue)).isTrue();
  }

  @Test
  public void kafkaPublisherPropertiesAreIgnoredWhenPrefixIsNotSet() {
    final String kafkaPropertyName = "fooBarBaz";
    final String kafkaPropertyValue = "aValue";
    globalPluginConfig.setBoolean(KAFKA_SECTION, KAFKA_PUBLISHER_SUBSECTION, ENABLE_KEY, true);
    globalPluginConfig.setString(
        KAFKA_SECTION, KAFKA_PUBLISHER_SUBSECTION, kafkaPropertyName, kafkaPropertyValue);

    final String property = getConfiguration().kafkaPublisher().getProperty("foo.bar.baz");

    assertThat(property).isNull();
  }

  @Test
  public void kafkaPublisherPropertiesAreNotSetWhenSectionIsDisabled() {
    final String kafkaPropertyName = KAFKA_PROPERTY_PREFIX + "fooBarBaz";
    final String kafkaPropertyValue = "aValue";
    globalPluginConfig.setBoolean(KAFKA_SECTION, KAFKA_PUBLISHER_SUBSECTION, ENABLE_KEY, false);
    globalPluginConfig.setString(
        KAFKA_SECTION, KAFKA_PUBLISHER_SUBSECTION, kafkaPropertyName, kafkaPropertyValue);

    final String property = getConfiguration().kafkaPublisher().getProperty("foo.bar.baz");

    assertThat(property).isNull();
  }
}
