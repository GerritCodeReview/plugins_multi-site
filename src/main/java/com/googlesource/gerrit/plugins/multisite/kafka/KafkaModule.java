package com.googlesource.gerrit.plugins.multisite.kafka;

import com.google.inject.AbstractModule;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration;
import com.googlesource.gerrit.plugins.multisite.forwarder.broker.BrokerForwarderModule;
import com.googlesource.gerrit.plugins.multisite.kafka.consumer.KafkaConsumerModule;
import com.googlesource.gerrit.plugins.multisite.kafka.router.ForwardedEventRouterModule;

public class KafkaModule extends AbstractModule {

  private KafkaConfiguration kafkaConfig;

  public KafkaModule(Configuration cfg) {
    this.kafkaConfig = new KafkaConfiguration(cfg.getMultiSiteConfig());
  }

  @Override
  protected void configure() {
    bind(KafkaConfiguration.class).toInstance(kafkaConfig);
    if (kafkaConfig.kafkaSubscriber().enabled()) {
      install(new KafkaConsumerModule(kafkaConfig.kafkaSubscriber()));
      install(new ForwardedEventRouterModule());
    }
    if (kafkaConfig.kafkaPublisher().enabled()) {
      install(new BrokerForwarderModule(kafkaConfig.kafkaPublisher()));
    }
  }
}
