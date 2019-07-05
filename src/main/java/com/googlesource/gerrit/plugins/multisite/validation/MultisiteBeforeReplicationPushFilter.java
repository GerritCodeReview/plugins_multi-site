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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.replication.BeforeReplicationPushFilter;
import java.util.List;
import org.eclipse.jgit.transport.RemoteRefUpdate;

@Singleton
public class MultisiteBeforeReplicationPushFilter implements BeforeReplicationPushFilter {
  private final SharedRefDatabase sharedRefDb;

  @Inject
  public MultisiteBeforeReplicationPushFilter(SharedRefDatabase sharedRefDb) {
    this.sharedRefDb = sharedRefDb;
  }

  @Override
  public List<RemoteRefUpdate> filter(String projectName, List<RemoteRefUpdate> remoteUpdatesList) {
    return SharedRefDbValidation.filterOutOfSyncRemoteRefUpdates(
        projectName, sharedRefDb, remoteUpdatesList);
  }
}
