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

import com.gerritforge.gerrit.globalrefdb.GlobalRefDatabase;
import com.gerritforge.gerrit.globalrefdb.GlobalRefDbLockException;
import com.gerritforge.gerrit.globalrefdb.GlobalRefDbSystemError;
import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

import java.util.Optional;

public class SharedRefDatabaseWrapper implements GlobalRefDatabase {

  @Inject(optional = true)
  private DynamicItem<GlobalRefDatabase> sharedRefDbDynamicItem;

  private final SharedRefLogger sharedRefLogger;

  @Inject
  public SharedRefDatabaseWrapper(SharedRefLogger sharedRefLogger) {
    this.sharedRefLogger = sharedRefLogger;
  }

  @VisibleForTesting
  public SharedRefDatabaseWrapper(
      DynamicItem<GlobalRefDatabase> sharedRefDbDynamicItem, SharedRefLogger sharedRefLogger) {
    this.sharedRefLogger = sharedRefLogger;
    this.sharedRefDbDynamicItem = sharedRefDbDynamicItem;
  }

  @Override
  public boolean isUpToDate(Project.NameKey project, Ref ref) throws GlobalRefDbLockException {
    return sharedRefDb().isUpToDate(project, ref);
  }

  @Override
  public boolean compareAndPut(Project.NameKey project, Ref currRef, ObjectId newRefValue)
      throws GlobalRefDbSystemError {
    boolean succeeded = sharedRefDb().compareAndPut(project, currRef, newRefValue);
    if (succeeded) {
      sharedRefLogger.logRefUpdate(project.get(), currRef, newRefValue);
    }
    return succeeded;
  }

  @Override
  public <T> boolean compareAndPut(Project.NameKey nameKey, String s, T t, T t1) throws GlobalRefDbSystemError {
    return false;
  }

  @Override
  public AutoCloseable lockRef(Project.NameKey project, String refName)
      throws GlobalRefDbLockException {
    AutoCloseable locker = sharedRefDb().lockRef(project, refName);
    sharedRefLogger.logLockAcquisition(project.get(), refName);
    return locker;
  }

  @Override
  public boolean exists(Project.NameKey project, String refName) {
    return sharedRefDb().exists(project, refName);
  }

  @Override
  public void remove(Project.NameKey project) throws GlobalRefDbSystemError {
    sharedRefDb().remove(project);
    sharedRefLogger.logProjectDelete(project.get());
  }

  @Override
  public <T> Optional <T> get(Project.NameKey nameKey, String s, Class<T> clazz) throws GlobalRefDbSystemError {
    return sharedRefDb().get(nameKey,s,clazz);
  }

  private GlobalRefDatabase sharedRefDb() {
    return sharedRefDbDynamicItem.get();
  }
}
