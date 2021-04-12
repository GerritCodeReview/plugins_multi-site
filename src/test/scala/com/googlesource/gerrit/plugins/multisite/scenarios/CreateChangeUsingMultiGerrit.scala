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
import io.gatling.core.Predef.{atOnceUsers, _}
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration._

class CreateChangeUsingMultiGerrit extends GerritSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  private val projectName = className

  override def relativeRuntimeWeight = 10

  private val test: ScenarioBuilder = scenario(uniqueName)
    .feed(data)
    .exec(httpRequest
      .body(ElFileBody(body)).asJson
      .check(regex("\"_" + numberKey + "\":(\\d+),").saveAs(numberKey)))
    .exec(session => {
      deleteChange.number = Some(session(numberKey).as[Int])
      session
    })

  private val createProject = new CreateProjectUsingMultiGerrit(projectName)
  private val deleteProject = new DeleteProjectUsingMultiGerrit(projectName)
  private val deleteChange = new DeleteChangeUsingMultiGerrit1

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(single)
    ),
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(single)
    ),
    deleteChange.test.inject(
      nothingFor(stepWaitTime(deleteChange) seconds),
      atOnceUsers(single)
    ).protocols(deleteChange.httpForReplica),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}
