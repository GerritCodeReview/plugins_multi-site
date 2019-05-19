package com.googlesource.gerrit.plugins.multisite.broker.kafka;

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;

public class ProducerProvider implements Provider<Producer<String, String>> {

  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private KafkaConfiguration kafkaConfig;

  @Inject
  public ProducerProvider(KafkaConfiguration kafkaConfig) {
    this.kafkaConfig = kafkaConfig;
  }

  @Override
  public Producer<String, String> get() {
    log.atInfo().log("Connect to %s...", kafkaConfig.getKafka().getBootstrapServers());
    return new KafkaProducer<>(kafkaConfig.kafkaPublisher());
  }
}
