// Copyright (C) 2020 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.scenarios

import com.google.gerrit.scenarios.GerritSimulation
import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef.{atOnceUsers, _}
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder

class DeleteChangeUsingMultiGerrit1 extends GerritSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  var number: Option[Int] = None

  override def relativeRuntimeWeight = 10

  override def replaceOverride(in: String): String = {
    replaceProperty("http_port1", 8081, in)
  }

  val httpForReplica: HttpProtocolBuilder = http.basicAuth(
    conf.httpConfiguration.userName,
    ConfigFactory.load().getString("http.password_replica"))

  val test: ScenarioBuilder = scenario(uniqueName)
    .feed(data)
    .exec(session => {
      if (number.nonEmpty) {
        session.set(numberKey, number.get)
      } else {
        session
      }
    })
    .exec(http(uniqueName).delete("${url}${"+ numberKey +"}"))

  setUp(
    test.inject(
      atOnceUsers(single)
    )).protocols(httpForReplica)
}
