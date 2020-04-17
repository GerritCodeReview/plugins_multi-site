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

import scala.concurrent.duration._

class CreateProjectUsingMultiGerritTwice extends GitSimulation {
  private val default: String = name

  private val createProject = new CreateProjectUsingMultiGerrit(default)
  private val deleteProject = new DeleteProjectUsingMultiGerrit(default)
  private val createItAgain = new CreateProjectUsingMultiGerrit(default)
  private val verifyProject = new CloneUsingMultiGerrit1(default)
  private val deleteItAfter = new DeleteProjectUsingMultiGerrit(default)

  setUp(
    createProject.test.inject(
      atOnceUsers(1)
    ),
    deleteProject.test.inject(
      nothingFor(createProject.max seconds),
      atOnceUsers(1)
    ),
    createItAgain.test.inject(
      nothingFor(createProject.max + deleteProject.max seconds),
      atOnceUsers(1)
    ),
    verifyProject.test.inject(
      nothingFor(createProject.max + deleteProject.max + createItAgain.max seconds),
      atOnceUsers(1)
    ),
    deleteItAfter.test.inject(
      nothingFor(createProject.max + deleteProject.max + createItAgain.max + verifyProject.max seconds),
      atOnceUsers(1)
    ),
  ).protocols(gitProtocol, httpProtocol)
}
