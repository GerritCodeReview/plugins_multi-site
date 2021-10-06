package com.googlesource.gerrit.plugins.multisite.tools

import java.util.Properties
import scala.jdk.CollectionConverters._
import scala.collection.mutable.{Map => MMap}


object BridgeConfig {

  def getConfig(): MMap[String,String] = {
    val properties: Properties = new Properties
    properties.load(getClass.getResourceAsStream("bridge.properties"))
    properties.asScala
  }

  val topics: Set[String] = Set("gerrit_stream")
}
