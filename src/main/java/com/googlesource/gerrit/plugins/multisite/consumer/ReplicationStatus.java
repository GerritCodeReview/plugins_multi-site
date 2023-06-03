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
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.serialize.JavaCacheSerializer;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class ReplicationStatus implements LifecycleListener, ProjectDeletedListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Map<String, Long> replicationStatusPerProject = new HashMap<>();
  static final String REPLICATION_STATUS = "replication_status";

  public static Module cacheModule(WorkQueue queue) {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(REPLICATION_STATUS, String.class, Long.class)
            .version(1)
            .keySerializer(StringCacheSerializer.INSTANCE)
            .valueSerializer(new JavaCacheSerializer<>());

        bind(ScheduledExecutorService.class)
            .annotatedWith(Names.named(REPLICATION_STATUS))
            .toInstance(queue.createQueue(0, REPLICATION_STATUS));
      }
    };
  }

  private final Map<String, Long> localVersionPerProject = new HashMap<>();
  private final Cache<String, Long> cache;
  private final Optional<ProjectVersionRefUpdate> projectVersionRefUpdate;
  private final ProjectVersionLogger verLogger;
  private final ProjectCache projectCache;
  private final ScheduledExecutorService statusScheduler;

  private final Configuration config;

  private MetricMaker metricMaker;

  @Inject
  public ReplicationStatus(
      @Named(REPLICATION_STATUS) Cache<String, Long> cache,
      Optional<ProjectVersionRefUpdate> projectVersionRefUpdate,
      ProjectVersionLogger verLogger,
      ProjectCache projectCache,
      @Named(REPLICATION_STATUS) ScheduledExecutorService statusScheduler,
      Configuration config,
      MetricMaker metricMaker) {
    this.cache = cache;
    this.projectVersionRefUpdate = projectVersionRefUpdate;
    this.verLogger = verLogger;
    this.projectCache = projectCache;
    this.statusScheduler = statusScheduler;
    this.config = config;
    this.metricMaker = metricMaker;
  }

  public Long getMaxLag() {
    Collection<Long> lags = replicationStatusPerProject.values();
    if (lags.isEmpty()) {
      return 0L;
    }
    return Collections.max(lags);
  }

  public Long getLagPerProject(String projectName){
    return replicationStatusPerProject.getOrDefault(projectName, 0L);
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
        projectVersionRefUpdate.flatMap(
            refUpdate -> refUpdate.getProjectRemoteVersion(projectName.get()));
    Optional<Long> localVersion =
        projectVersionRefUpdate.flatMap(
            refUpdate -> refUpdate.getProjectLocalVersion(projectName.get()));
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

  void removeProjectFromReplicationLagMetrics(Project.NameKey projectName) {
    Optional<Long> localVersion =
        projectVersionRefUpdate.get().getProjectLocalVersion(projectName.get());

    if (!localVersion.isPresent() && replicationStatusPerProject.containsKey(projectName.get())) {
      cache.invalidate(projectName.get());
      replicationStatusPerProject.remove(projectName.get());
      localVersionPerProject.remove(projectName.get());
      verLogger.logDeleted(projectName);
      logger.atFine().log("Removed project '%s' from replication lag metrics", projectName);
    }
  }

  @VisibleForTesting
  public void doUpdateLag(Project.NameKey projectName, Long lag) {
    cache.put(projectName.get(), lag);
    replicationStatusPerProject.put(projectName.get(), lag);
    
    if(lag > 0) {
    logger.atWarning().log("Creating replication lag metric for project %s", projectName);
    metricMaker.newCallbackMetric(
    		SubscriberMetrics.REPLICATION_LAG_SEC + "_" + projectName.get(),
        Long.class,
        new Description("Replication lag for project (sec)").setGauge().setUnit(Description.Units.SECONDS),
        () -> getLagPerProject(projectName.get())
    );
    }
  }

  @VisibleForTesting
  Long getReplicationStatus(String projectName) {
    return replicationStatusPerProject.get(projectName);
  }

  @VisibleForTesting
  Long getLocalVersion(String projectName) {
    return localVersionPerProject.get(projectName);
  }

  @Override
  public void start() {
    loadAllFromCache();

    long replicationLagPollingInterval = config.replicationLagRefreshInterval().toMillis();

    if (replicationLagPollingInterval > 0) {
      statusScheduler.scheduleAtFixedRate(
          this::refreshProjectsWithLag,
          replicationLagPollingInterval,
          replicationLagPollingInterval,
          TimeUnit.MILLISECONDS);
    }
  }

  @VisibleForTesting
  public void refreshProjectsWithLag() {
    logger.atFine().log("Refreshing projects version lags triggered ...");
    replicationStatusPerProject.entrySet().stream()
        .filter(entry -> entry.getValue() > 0)
        .map(Map.Entry::getKey)
        .map(Project::nameKey)
        .forEach(this::updateReplicationLag);
  }

  @Override
  public void stop() {}

  private void loadAllFromCache() {
    Set<String> cachedProjects =
        projectCache.all().stream().map(Project.NameKey::get).collect(Collectors.toSet());
    replicationStatusPerProject.putAll(cache.getAllPresent(cachedProjects));
  }

  @Override
  public void onProjectDeleted(Event event) {
    removeProjectFromReplicationLagMetrics(Project.nameKey(event.getProjectName()));
  }
}
