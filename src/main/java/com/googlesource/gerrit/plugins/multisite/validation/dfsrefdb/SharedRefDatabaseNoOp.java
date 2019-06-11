package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class SharedRefDatabaseNoOp implements SharedRefDatabase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public boolean compareAndCreate(String project, Ref newRef) throws IOException {
    logger.atFine().log(
        "SharedRefDb NoOp - compareAndCreate(%s,%s). Will return true", project, newRef);
    return true;
  }

  @Override
  public boolean isUpToDate(String project, Ref ref) throws SharedLockException {
    logger.atFine().log("SharedRefDb NoOp - isUpToDate(%s,%s). Will return true", project, ref);
    return true;
  }

  @Override
  public boolean compareAndPut(String project, Ref currRef, ObjectId newRefValue)
      throws IOException {
    logger.atFine().log(
        "SharedRefDb NoOp - compareAndPut(%s, %s, %s). Will return true",
        project, currRef, newRefValue);
    return true;
  }

  @Override
  public boolean compareAndRemove(String project, Ref oldRef) throws IOException {
    logger.atFine().log(
        "SharedRefDb NoOp - compareAndRemove(%s, %s). Will return true", project, oldRef);
    return true;
  }

  @Override
  public AutoCloseable lockRef(String project, String refName) throws SharedLockException {
    logger.atFine().log("SharedRefDb NoOp - lockRef(%s, %s). Will return null", project, refName);
    return null;
  }

  @Override
  public boolean exists(String project, String refName) {
    logger.atFine().log("SharedRefDb NoOp - exists(%s, %s). Will return true", project, refName);
    return true;
  }

  @Override
  public void removeProject(String project) throws IOException {
    logger.atFine().log("SharedRefDb NoOp - removeProject(%s)", project);
  }
}
