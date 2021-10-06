package com.googlesource.gerrit.plugins.multisite.tools

import com.googlesource.gerrit.plugins.multisite.tools.BrokerBridge.actorSystem
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import akka.kafka._
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.duration.DurationInt

object BridgeKafkaConsumer {

  //TODO Read from config
  private val groupId = "BrokerBridgeConsumer"
  private val kafkaBootstrapServers = "http://localhost:9092"

  val settings = ConsumerSettings(actorSystem.toClassic, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(kafkaBootstrapServers)
    .withGroupId(groupId)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    .withStopTimeout(0.seconds)

}
