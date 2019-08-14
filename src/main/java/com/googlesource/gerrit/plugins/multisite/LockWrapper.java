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
import com.google.inject.assistedinject.Assisted;

public class LockWrapper implements AutoCloseable {
  public interface Factory {
    LockWrapper create(
        @Assisted("project") String project,
        @Assisted("refName") String refName,
        @Assisted("lock") AutoCloseable lock);
  }

  private final String project;
  private final String refName;
  private final AutoCloseable lock;
  private final SharedRefLogger sharedRefLogger;

  @Inject
  public LockWrapper(
      SharedRefLogger sharedRefLogger,
      @Assisted("project") String project,
      @Assisted("refName") String refName,
      @Assisted("lock") AutoCloseable lock) {
    this.lock = lock;
    this.sharedRefLogger = sharedRefLogger;
    this.project = project;
    this.refName = refName;
  }

  @Override
  public void close() throws Exception {
    lock.close();
    sharedRefLogger.logLockRelease(project, refName);
  }
}
