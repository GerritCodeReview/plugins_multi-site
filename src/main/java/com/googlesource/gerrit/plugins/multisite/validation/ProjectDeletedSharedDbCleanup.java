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

package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.SharedRefLogger;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.IOException;

public class ProjectDeletedSharedDbCleanup implements ProjectDeletedListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SharedRefDatabase sharedDb;

  private final ValidationMetrics validationMetrics;
  private final SharedRefLogger sharedRefLogger;

  @Inject
  public ProjectDeletedSharedDbCleanup(
      SharedRefDatabase sharedDb,
      ValidationMetrics validationMetrics,
      SharedRefLogger sharedRefLogger) {
    this.sharedDb = sharedDb;
    this.validationMetrics = validationMetrics;
    this.sharedRefLogger = sharedRefLogger;
  }

  @Override
  public void onProjectDeleted(Event event) {
    String projectName = event.getProjectName();
    logger.atInfo().log(
        "Deleting project '%s'. Will perform a cleanup in Shared-Ref database.", projectName);

    try {
      sharedDb.removeProject(projectName);
      sharedRefLogger.logProjectDelete(projectName);
    } catch (IOException e) {
      validationMetrics.incrementSplitBrain();
      logger.atSevere().withCause(e).log(
          "Project '%s' deleted from GIT but it was not able to cleanup"
              + " from Shared-Ref database",
          projectName);
    }
  }
}
