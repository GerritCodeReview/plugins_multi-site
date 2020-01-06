// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.util.*;

/**
 * This class stores the replication status of a Gerrit instance
 *
 * <p>The status is represented per project but also globally. The global replication status is, for
 * example, the max replication timestamp of all the projects. The replication Status of a project
 * is represented by {@see com.googlesource.gerrit.plugins.multisite.ProjectReplicationStatus}
 */
@Singleton
public class ReplicationStatusStore {

  private Map<String, ProjectReplicationStatus> statusPerProject;
  private Long globalLastReplicationTime;

  public ReplicationStatusStore() {
    this.statusPerProject = new HashMap<String, ProjectReplicationStatus>();
  }

  public void updateLastReplicationTime(String projectName, Long timestamp) {
    ProjectReplicationStatus projectReplicationStatus = new ProjectReplicationStatus(timestamp);
    this.statusPerProject.put(projectName, projectReplicationStatus);
    this.globalLastReplicationTime = timestamp;
  }

  public Optional<Long> getLastReplicationTime(String projectName) {
    Optional<ProjectReplicationStatus> maybeProjectReplicationStatus =
        Optional.ofNullable(this.statusPerProject.get(projectName));
    return maybeProjectReplicationStatus.map(ProjectReplicationStatus::getLastReplicationTimestamp);
  }

  public Long getGlobalLastReplicationTime() {
    return this.globalLastReplicationTime;
  }

  @VisibleForTesting
  public void emptyReplicationTimePerProject() {
    this.statusPerProject.clear();
  }
}
