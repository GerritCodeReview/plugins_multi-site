package com.googlesource.gerrit.plugins.multisite.tools;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.googlesource.gerrit.plugins.multisite.tools.consumer.BridgeKafkaConsumer;
import com.googlesource.gerrit.plugins.multisite.tools.producer.BridgeKinesisProducer;

public class MessageBridge {
  public static void main(String[] args) {
    System.out.println("Starting message bridge Kafka - AWS Kinesis!");

    String broker1 = "kafka";
    String broker2 = "kinesis";



    BridgeKinesisProducer kinesisProducer = new BridgeKinesisProducer(new Config());

    BridgeKafkaConsumer.runConsumer(kinesisProducer);
  }
}
