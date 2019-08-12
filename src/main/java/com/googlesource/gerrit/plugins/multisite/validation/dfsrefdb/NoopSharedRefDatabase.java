package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class NoopSharedRefDatabase implements SharedRefDatabase {
  @Override
  public boolean isUpToDate(String project, Ref ref) throws SharedLockException {
    return true;
  }

  @Override
  public boolean compareAndPut(String project, Ref currRef, ObjectId newRefValue)
      throws IOException {
    return true;
  }

  @Override
  public AutoCloseable lockRef(String project, String refName) throws SharedLockException {
    return () -> {};
  }

  @Override
  public boolean exists(String project, String refName) {
    return true;
  }

  @Override
  public void removeProject(String project) throws IOException {}
}
