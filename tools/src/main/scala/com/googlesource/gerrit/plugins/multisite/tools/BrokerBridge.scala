package com.googlesource.gerrit.plugins.multisite.tools

import akka.Done
import akka.actor.CoordinatedShutdown

import scala.concurrent.{Await, ExecutionContext, Future}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.{CommitterSettings, Subscriptions}
import akka.actor.typed.scaladsl.adapter._
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.stream.scaladsl.{Keep, Sink}
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.duration.DurationInt

object BrokerBridge extends App {

  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "BrokerBridge")
  implicit val executionContext: ExecutionContext = actorSystem.executionContext

  //TODO add logic to skip local produced messages

  private def forwardRecordsFromKafkaToKinesis() = {

    def businessLogic(record: String): Future[Done] = {
      println(s"DB.save: ${record}")
      Future.successful(Done)
    }

    val control = Consumer.sourceWithOffsetContext(BridgeKafkaConsumer.settings, Subscriptions.topics(BridgeConfig.topics))
      .map(_.value()) // Transform Kafka ConsumerRecord in string
      .mapAsync(1)(businessLogic) // Business logic here
      .via(Committer.flowWithOffsetContext(CommitterSettings(actorSystem.toClassic))) // Commit Kafka Offset
      .toMat(Sink.seq)(DrainingControl.apply)
      .run()

    sys.ShutdownHookThread {
      println("\uD83D\uDD0C Shutting down bridge \uD83D\uDD0C")
      Await.result(control.drainAndShutdown(), 1.minute)
    }

  }

  println("\uD83C\uDF09 Starting bridge \uD83C\uDF09")
  forwardRecordsFromKafkaToKinesis()

}
