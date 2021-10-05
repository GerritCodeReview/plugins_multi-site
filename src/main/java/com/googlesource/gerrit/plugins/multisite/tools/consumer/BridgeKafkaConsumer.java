package com.googlesource.gerrit.plugins.multisite.tools.consumer;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.googlesource.gerrit.plugins.multisite.tools.Config;
import com.googlesource.gerrit.plugins.multisite.tools.producer.BridgeProducer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import sun.misc.Signal;

import static com.googlesource.gerrit.plugins.multisite.tools.Config.TOPICS;

public class BridgeKafkaConsumer {
  Config config;

  public BridgeKafkaConsumer(Config c) {
    config = c;
  }

  private Consumer<String, String> createConsumer() {
    final Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.KAFKA_BOOTSTRAP_SERVERS);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, config.KAFKA_GROUP_ID);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    final Consumer<String, String> consumer = new KafkaConsumer<>(props);

    consumer.subscribe(TOPICS);
    return consumer;
  }

  public void runConsumer(BridgeProducer producer) {
    AtomicBoolean interrupted = new AtomicBoolean(false);
    Signal.handle(new Signal("INT"),  // SIGINT
          signal -> interrupted.set(true));

    HashMap<String, Integer> recordCount = new HashMap<>();

    try (Consumer<String, String> consumer = createConsumer()) {
      while (!interrupted.get()) {

        final ConsumerRecords<String, String> consumerRecords =
            consumer.poll(Duration.ofMillis(1000));

        if (consumerRecords.isEmpty()) {
          try {
            System.out.println("No records to consumer sleeping for 5 seconds...");
            Thread.sleep(5000);
          } catch (InterruptedException ignore) {
          }
          continue;
        }
        consumerRecords.forEach(
            record -> {
              recordCount.put(record.topic(), recordCount.getOrDefault(record.topic(), 0) + 1);
              System.out.printf(
                  "Consuming record for topic: %s, %s, %s)\n",
                  record.topic(), record.key(), record.value());
              producer.sendRecord(record.topic(), record.value());
            });

        consumer.commitAsync();
      }
    } catch (WakeupException e) {
      // Do nothing
    } finally {
      System.out.printf("Shutting down Kafka consumer. Stats: %s\n", recordCount.toString());
    }
  }
}
