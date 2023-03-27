// Copyright (C) 2023 The Android Open Source Project
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

import com.gerritforge.gerrit.globalrefdb.GlobalRefDatabase;
import com.gerritforge.gerrit.globalrefdb.GlobalRefDbLockException;
import com.gerritforge.gerrit.globalrefdb.GlobalRefDbSystemError;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDBMetrics;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.metrics.DisabledMetricMaker;
import java.util.Arrays;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

class FakeSharedRefDatabaseWrapper extends SharedRefDatabaseWrapper {

  public FakeSharedRefDatabaseWrapper(String... rejectedRefs) {

    super(
        DynamicItem.itemOf(
            GlobalRefDatabase.class,
            new GlobalRefDatabase() {

              @Override
              public boolean isUpToDate(Project.NameKey project, Ref ref)
                  throws GlobalRefDbLockException {
                return !Arrays.stream(rejectedRefs).anyMatch(r -> r.equals(ref.getName()));
              }

              @Override
              public boolean exists(Project.NameKey project, String refName) {
                return true;
              }

              @Override
              public boolean compareAndPut(
                  Project.NameKey project, Ref currRef, ObjectId newRefValue)
                  throws GlobalRefDbSystemError {
                return false;
              }

              @Override
              public <T> boolean compareAndPut(
                  Project.NameKey project, String refName, T currValue, T newValue)
                  throws GlobalRefDbSystemError {
                return false;
              }

              @Override
              public AutoCloseable lockRef(Project.NameKey project, String refName)
                  throws GlobalRefDbLockException {
                return null;
              }

              @Override
              public void remove(Project.NameKey project) throws GlobalRefDbSystemError {}

              @Override
              public <T> Optional<T> get(Project.NameKey project, String refName, Class<T> clazz)
                  throws GlobalRefDbSystemError {
                return Optional.empty();
              }
            }),
        new DisabledSharedRefLogger(),
        new SharedRefDBMetrics(new DisabledMetricMaker()));
  }
}
