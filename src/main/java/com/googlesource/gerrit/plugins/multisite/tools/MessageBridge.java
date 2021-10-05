package com.googlesource.gerrit.plugins.multisite.tools;

import com.googlesource.gerrit.plugins.multisite.tools.consumer.BridgeKafkaConsumer;
import com.googlesource.gerrit.plugins.multisite.tools.producer.BridgeKinesisProducer;

public class MessageBridge {
  public static void main(String[] args) {
    System.out.println("Starting message bridge Kafka - AWS Kinesis!");

    String broker1 = "kafka";
    String broker2 = "kinesis";

    BridgeKinesisProducer kinesisProducer = new BridgeKinesisProducer(Config.getInstance());
    BridgeKafkaConsumer kafkaConsumer = new BridgeKafkaConsumer(Config.getInstance());

    kafkaConsumer.runConsumer(kinesisProducer);
  }
}
