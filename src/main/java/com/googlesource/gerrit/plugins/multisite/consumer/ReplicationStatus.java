// Copyright (C) 2021 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.consumer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class ReplicationStatus {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Map<String, Long> replicationStatusPerProject = new HashMap<>();
  private final Map<String, Long> localVersionPerProject = new HashMap<>();
  private final ProjectVersionRefUpdate projectVersionRefUpdate;
  private final ProjectVersionLogger verLogger;

  @Inject
  public ReplicationStatus(
      ProjectVersionRefUpdate projectVersionRefUpdate, ProjectVersionLogger verLogger) {
    this.projectVersionRefUpdate = projectVersionRefUpdate;
    this.verLogger = verLogger;
  }

  public Long getMaxLag() {
    Collection<Long> lags = replicationStatusPerProject.values();
    if (lags.isEmpty()) {
      return 0L;
    }
    return Collections.max(lags);
  }

  public Map<String, Long> getReplicationLag(Integer limit) {
    return replicationStatusPerProject.entrySet().stream()
        .sorted((c1, c2) -> c2.getValue().compareTo(c1.getValue()))
        .limit(limit)
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (oldValue, newValue) -> oldValue,
                LinkedHashMap::new));
  }

  public void updateReplicationLag(Project.NameKey projectName) {
    Optional<Long> remoteVersion =
        projectVersionRefUpdate.getProjectRemoteVersion(projectName.get());
    Optional<Long> localVersion = projectVersionRefUpdate.getProjectLocalVersion(projectName.get());
    if (remoteVersion.isPresent() && localVersion.isPresent()) {
      long lag = remoteVersion.get() - localVersion.get();

      if (!localVersion.get().equals(localVersionPerProject.get(projectName.get()))
          || lag != replicationStatusPerProject.get(projectName.get())) {
        logger.atFine().log(
            "Updated replication lag for project '%s' of %d sec(s) [local-ref=%d global-ref=%d]",
            projectName, lag, localVersion.get(), remoteVersion.get());
        doUpdateLag(projectName, lag);
        localVersionPerProject.put(projectName.get(), localVersion.get());
        verLogger.log(projectName, localVersion.get(), lag);
      }
    } else {
      logger.atFine().log(
          "Did not update replication lag for %s because the %s version is not defined",
          projectName, localVersion.isPresent() ? "remote" : "local");
    }
  }

  @VisibleForTesting
  public void doUpdateLag(Project.NameKey projectName, Long lag) {
    replicationStatusPerProject.put(projectName.get(), lag);
  }
}
