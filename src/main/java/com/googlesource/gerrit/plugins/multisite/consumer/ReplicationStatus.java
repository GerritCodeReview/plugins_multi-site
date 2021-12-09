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
import com.google.common.cache.Cache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.serialize.JavaCacheSerializer;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ReplicationStatus implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Map<String, Long> replicationStatusPerProject = new HashMap<>();
  static final String REPLICATION_STATUS_CACHE = "replication_status";

  public static Module cacheModule() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(REPLICATION_STATUS_CACHE, String.class, Long.class)
            .version(1)
            .keySerializer(StringCacheSerializer.INSTANCE)
            .valueSerializer(new JavaCacheSerializer<>());
      }
    };
  }

  private final Map<String, Long> localVersionPerProject = new HashMap<>();
  private final Cache<String, Long> cache;
  private final ProjectVersionRefUpdate projectVersionRefUpdate;
  private final ProjectVersionLogger verLogger;
  private final ProjectCache projectCache;

  @Inject
  public ReplicationStatus(
      @Named(REPLICATION_STATUS_CACHE) Cache<String, Long> cache,
      ProjectVersionRefUpdate projectVersionRefUpdate,
      ProjectVersionLogger verLogger,
      ProjectCache projectCache) {
    this.cache = cache;
    this.projectVersionRefUpdate = projectVersionRefUpdate;
    this.verLogger = verLogger;
    this.projectCache = projectCache;
  }

  public Long getMaxLag() {
    Collection<Long> lags = replicationStatusPerProject.values();
    if (lags.isEmpty()) {
      return 0L;
    }
    return Collections.max(lags);
  }

  public Map<String, Long> getReplicationLags(Integer limit) {
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
    cache.put(projectName.get(), lag);
    replicationStatusPerProject.put(projectName.get(), lag);
  }

  @Override
  public void start() {
    loadAllFromCache();
  }

  @Override
  public void stop() {}

  private void loadAllFromCache() {
    Set<String> cachedProjects =
        projectCache.all().stream().map(Project.NameKey::get).collect(Collectors.toSet());
    replicationStatusPerProject.putAll(cache.getAllPresent(cachedProjects));
  }
}
