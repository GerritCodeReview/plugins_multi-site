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

package com.googlesource.gerrit.plugins.multisite;

import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedLockException;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class SharedRefDatabaseWrapper implements SharedRefDatabase {

  private final SharedRefDatabase sharedRefDb;
  private final SharedRefLogger sharedRefLogger;

  @Inject
  public SharedRefDatabaseWrapper(
      SharedRefDatabase sharedRefDatabase, SharedRefLogger sharedRefLogger) {
    this.sharedRefDb = sharedRefDatabase;
    this.sharedRefLogger = sharedRefLogger;
  }

  @Override
  public boolean isUpToDate(String project, Ref ref) throws SharedLockException {
    return sharedRefDb.isUpToDate(project, ref);
  }

  @Override
  public boolean compareAndPut(String project, Ref currRef, ObjectId newRefValue)
      throws IOException {
    boolean succeeded = sharedRefDb.compareAndPut(project, currRef, newRefValue);
    if (succeeded) {
      sharedRefLogger.logRefUpdate(project, currRef, newRefValue);
    }
    return succeeded;
  }

  @Override
  public AutoCloseable lockRef(String project, String refName) throws SharedLockException {
    AutoCloseable locker = sharedRefDb.lockRef(project, refName);
    sharedRefLogger.logLockAcquisition(project, refName);
    return locker;
  }

  @Override
  public boolean exists(String project, String refName) {
    return sharedRefDb.exists(project, refName);
  }

  @Override
  public void removeProject(String project) throws IOException {
    sharedRefDb.removeProject(project);
    sharedRefLogger.logProjectDelete(project);
  }
}
