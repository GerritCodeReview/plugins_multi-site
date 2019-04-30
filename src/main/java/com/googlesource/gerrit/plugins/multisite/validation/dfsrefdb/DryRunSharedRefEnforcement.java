// Copyright (C) 2019 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import com.google.common.flogger.FluentLogger;

public class DryRunSharedRefEnforcement implements SharedRefEnforcement {
  private final FluentLogger logger = FluentLogger.forEnclosingClass();

  public DryRunSharedRefEnforcement() {
    logger.atInfo().log("Running with Shared Ref-Db DryRun Enforcement Policy");
  }

  @Override
  public EnforcePolicy getPolicy(String projectName, String refName) {
    if (isRefToBeIgnoredBySharedRefDb(refName)) {
      return EnforcePolicy.IGNORED;
    }

    return EnforcePolicy.DESIRED;
  }

  @Override
  public EnforcePolicy getPolicy(String projectName) {
    return EnforcePolicy.DESIRED;
  }
}
