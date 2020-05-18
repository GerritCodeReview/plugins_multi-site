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

import com.google.gerrit.scenarios.GitSimulation
import io.gatling.core.Predef.{atOnceUsers, _}
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._

class CloneUsingMultiGerrit1 extends GitSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  private var default: String = name

  def this(default: String) {
    this()
    this.default = default
  }

  override def replaceOverride(in: String): String = {
    val next = replaceProperty("http_port1", 8081, in)
    replaceKeyWith("_project", default, next)
  }

  val test: ScenarioBuilder = scenario(unique)
    .feed(data)
    .exec(gitRequest)

  private val createProject = new CreateProjectUsingMultiGerrit(default)
  private val deleteProject = new DeleteProjectUsingMultiGerrit(default)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(1)
    ),
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(1)
    ),
    deleteProject.test.inject(
      nothingFor(createProject.maxExecutionTime + maxExecutionTime seconds),
      atOnceUsers(1)
    ),
  ).protocols(gitProtocol, httpProtocol)
}
