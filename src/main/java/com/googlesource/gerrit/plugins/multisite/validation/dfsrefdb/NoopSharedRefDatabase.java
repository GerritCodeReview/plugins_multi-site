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

import com.gerritforge.gerrit.globalrefdb.GlobalRefDatabase;
import com.gerritforge.gerrit.globalrefdb.GlobalRefDbLockException;
import com.gerritforge.gerrit.globalrefdb.GlobalRefDbSystemError;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class NoopSharedRefDatabase implements GlobalRefDatabase {

  @Override
  public boolean isUpToDate(NameKey project, Ref ref) throws GlobalRefDbLockException {
    return true;
  }

  @Override
  public boolean compareAndPut(NameKey project, Ref currRef, ObjectId newRefValue)
      throws GlobalRefDbSystemError {
    return true;
  }

  @Override
  public AutoCloseable lockRef(NameKey project, String refName) throws GlobalRefDbLockException {
    return () -> {};
  }

  @Override
  public boolean exists(NameKey project, String refName) {
    return false;
  }

  @Override
  public void remove(NameKey project) throws GlobalRefDbSystemError {}
}
