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

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.multisite.Configuration.KafkaConfiguration.ENABLE_KEY;
import static com.googlesource.gerrit.plugins.multisite.Configuration.KafkaConfiguration.KAFKA_PROPERTY_PREFIX;
import static com.googlesource.gerrit.plugins.multisite.Configuration.KafkaConfiguration.KAFKA_SECTION;
import static com.googlesource.gerrit.plugins.multisite.Configuration.KafkaPublisher.KAFKA_PUBLISHER_SUBSECTION;
import static com.googlesource.gerrit.plugins.multisite.Configuration.KafkaSubscriber.KAFKA_SUBSCRIBER_SUBSECTION;

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

  private Configuration.KafkaConfiguration getConfiguration() {
    return new Configuration.KafkaConfiguration(globalPluginConfig);
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
