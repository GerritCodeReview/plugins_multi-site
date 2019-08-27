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

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.multisite.kafka.KafkaConfiguration.ENABLE_KEY;
import static com.googlesource.gerrit.plugins.multisite.kafka.KafkaConfiguration.KAFKA_PROPERTY_PREFIX;
import static com.googlesource.gerrit.plugins.multisite.kafka.KafkaConfiguration.KAFKA_SECTION;
import static com.googlesource.gerrit.plugins.multisite.kafka.KafkaConfiguration.KafkaPublisher.KAFKA_PUBLISHER_SUBSECTION;
import static com.googlesource.gerrit.plugins.multisite.kafka.KafkaConfiguration.KafkaSubscriber.KAFKA_SUBSCRIBER_SUBSECTION;

import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KafkaConfigurationTest {

  private Config globalPluginConfig;
  private Configuration multiSiteConfig;

  @Before
  public void setup() {
    globalPluginConfig = new Config();
    multiSiteConfig = new Configuration(globalPluginConfig, new Config());
  }

  private KafkaConfiguration getConfiguration() {
    return new KafkaConfiguration(multiSiteConfig);
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

  @Test
  public void shouldReturnKafkaTopicAliasForIndexTopic() {
    setKafkaTopicAlias("indexEventTopic", "gerrit_index");
    final String property = getConfiguration().getKafka().getTopicAlias(EventTopic.INDEX_TOPIC);

    assertThat(property).isEqualTo("gerrit_index");
  }

  @Test
  public void shouldReturnKafkaTopicAliasForStreamEventTopic() {
    setKafkaTopicAlias("streamEventTopic", "gerrit_stream_events");
    final String property =
        getConfiguration().getKafka().getTopicAlias(EventTopic.STREAM_EVENT_TOPIC);

    assertThat(property).isEqualTo("gerrit_stream_events");
  }

  @Test
  public void shouldReturnKafkaTopicAliasForProjectListEventTopic() {
    setKafkaTopicAlias("projectListEventTopic", "gerrit_project_list");
    final String property =
        getConfiguration().getKafka().getTopicAlias(EventTopic.PROJECT_LIST_TOPIC);

    assertThat(property).isEqualTo("gerrit_project_list");
  }

  @Test
  public void shouldReturnKafkaTopicAliasForCacheEventTopic() {
    setKafkaTopicAlias("cacheEventTopic", "gerrit_cache");
    final String property = getConfiguration().getKafka().getTopicAlias(EventTopic.CACHE_TOPIC);

    assertThat(property).isEqualTo("gerrit_cache");
  }

  private void setKafkaTopicAlias(String topicKey, String topic) {
    globalPluginConfig.setString(KAFKA_SECTION, null, topicKey, topic);
  }
}
