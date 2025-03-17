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

import static com.googlesource.gerrit.plugins.replication.pull.PullReplicationLogger.repLog;

import com.gerritforge.gerrit.globalrefdb.GlobalRefDbLockException;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.replication.pull.ReplicationFetchFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class MultisiteReplicationFetchFilter extends AbstractMultisiteReplicationFilter
    implements ReplicationFetchFilter {
  private static final String ZERO_ID_NAME = ObjectId.zeroId().name();
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SharedRefDatabaseWrapper sharedRefDb;
  private final GitRepositoryManager gitRepositoryManager;
  private Configuration config;

  @Inject
  public MultisiteReplicationFetchFilter(
      SharedRefDatabaseWrapper sharedRefDb,
      GitRepositoryManager gitRepositoryManager,
      Configuration config) {
    this.sharedRefDb = sharedRefDb;
    this.gitRepositoryManager = gitRepositoryManager;
    this.config = config;
  }

  @Override
  public Set<String> filter(String projectName, Set<String> refs) {
    try (Repository repository =
        gitRepositoryManager.openRepository(Project.nameKey(projectName))) {
      RefDatabase refDb = repository.getRefDatabase();
      return refs.stream()
          .filter(
              ref -> {
                if (shouldNotBeTrackedAnymoreOnGlobalRefDb(ref)) {
                  return true;
                }
                Optional<ObjectId> localRefOid =
                    getLocalSha1IfEqualsToExistingGlobalRefDb(
                        repository, projectName, refDb, ref, true);
                localRefOid.ifPresent(
                    oid ->
                        repLog.info(
                            "{}:{}={} is already up-to-date with the shared-refdb and thus will NOT"
                                + " BE fetched",
                            projectName,
                            ref,
                            oid.getName()));

                return !localRefOid.isPresent();
              })
          .collect(Collectors.toSet());
    } catch (IOException ioe) {
      String message = String.format("Error while opening project: '%s'", projectName);
      repLog.error(message);
      logger.atSevere().withCause(ioe).log("%s", message);
      return Collections.emptySet();
    }
  }

  @Override
  public Map<String, AutoCloseable> filterAndLock(String projectName, Set<String> fetchRefs) {
    Project.NameKey projectKey = Project.nameKey(projectName);
    Set<String> filteredRefs = new HashSet<>();
    Map<String, AutoCloseable> refLocks = new HashMap<>();
    try {
      refLocks.putAll(
          fetchRefs.stream()
              .collect(Collectors.toMap(ref -> ref, ref -> sharedRefDb.lockLocalRef(projectKey, ref))));
      filteredRefs.addAll(filter(projectName, fetchRefs));
    } finally {
      fetchRefs.forEach(
          ref -> {
            if (!filteredRefs.contains(ref)) {
              try {
                refLocks.remove(ref).close();
              } catch (Exception e) {
                logger.atWarning().withCause(e).log("Error whilst unlocking ref %s", ref);
              }
            }
          });
    }

    return refLocks;
  }

  private Optional<ObjectId> getLocalSha1IfEqualsToExistingGlobalRefDb(
      Repository repository,
      String projectName,
      RefDatabase refDb,
      String ref,
      boolean retryWithRandomSleep) {
    try {
      Optional<ObjectId> localRefObjectId =
          Optional.ofNullable(refDb.exactRef(ref))
              .filter(
                  r ->
                      sharedRefDb
                          .get(Project.nameKey(projectName), r.getName(), String.class)
                          .map(sharedRefObjId -> r.getObjectId().getName().equals(sharedRefObjId))
                          .orElse(false))
              .map(Ref::getObjectId);

      if (!localRefObjectId.isPresent() && retryWithRandomSleep) {
        randomSleepForMitigatingConditionWhereLocalRefHaveJustBeenChanged(
            projectName, localRefObjectId, ref);
        localRefObjectId =
            getLocalSha1IfEqualsToExistingGlobalRefDb(repository, projectName, refDb, ref, false);
      }

      return localRefObjectId;
    } catch (GlobalRefDbLockException gle) {
      String message = String.format("%s is locked on shared-refdb", ref);
      repLog.error(message);
      logger.atSevere().withCause(gle).log("%s", message);
      return Optional.empty();
    } catch (IOException ioe) {
      String message =
          String.format("Error while extracting ref '%s' for project '%s'", ref, projectName);
      repLog.error(message);
      logger.atSevere().withCause(ioe).log("%s", message);
      return Optional.empty();
    }
  }

  private void randomSleepForMitigatingConditionWhereLocalRefHaveJustBeenChanged(
      String projectName, Optional<ObjectId> refObjectId, String ref) {
    if (!config.replicationFilter().isFetchFilterRandomSleepEnabled()) {
      repLog.debug(
          "'{}' is not up-to-date for project '{}' [local='{}']. Random sleep is disabled,"
              + " reload local ref without delay and re-check",
          ref,
          projectName,
          refObjectId);
      return;
    }

    int randomSleepTimeMsec = config.replicationFilter().fetchFilterRandomSleepTimeMs();
    repLog.debug(
        "'{}' is not up-to-date for project '{}' [local='{}']. Reload local ref in '{} ms' and"
            + " re-check",
        ref,
        projectName,
        refObjectId,
        randomSleepTimeMsec);
    try {
      Thread.sleep(randomSleepTimeMsec);
    } catch (InterruptedException ie) {
      String message =
          String.format("Error while waiting for next check for '%s', ref '%s'", projectName, ref);
      repLog.error(message);
      logger.atWarning().withCause(ie).log("%s", message);
    }
  }
}
