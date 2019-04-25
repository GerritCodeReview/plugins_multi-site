package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.IOException;

public class ProjectDeletedSharedDbCleanup implements ProjectDeletedListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SharedRefDatabase sharedDb;

  @Inject
  public ProjectDeletedSharedDbCleanup(SharedRefDatabase sharedDb) {
    this.sharedDb = sharedDb;
  }

  @Override
  public void onProjectDeleted(Event event) {
    String projectName = event.getProjectName();
    logger.atInfo().log(
        "Deleting project '%s'. Will perform a cleanup in Shared-Ref database.", projectName);

    try {
      sharedDb.removeProject(projectName);
    } catch (IOException e) {
      // TODO: Add metrics for monitoring if it fails to delete
      logger.atWarning().log(
          String.format(
              "Project '%s' deleted from GIT but it was not able to fully cleanup"
                  + " from Shared-Ref database",
              projectName),
          e);
    }
  }
}
