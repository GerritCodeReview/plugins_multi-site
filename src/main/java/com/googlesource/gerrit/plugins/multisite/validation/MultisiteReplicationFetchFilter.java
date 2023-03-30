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
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.replication.pull.ReplicationFetchFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class MultisiteReplicationFetchFilter implements ReplicationFetchFilter {
  private static final String ZERO_ID_NAME = ObjectId.zeroId().name();
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final int MIN_WAIT_BEFORE_RELOAD_LOCAL_VERSION_MS = 1000;
  public static final int RANDOM_WAIT_BEFORE_RELOAD_LOCAL_VERSION_MS = 1000;

  private final SharedRefDatabaseWrapper sharedRefDb;
  private final GitRepositoryManager gitRepositoryManager;

  @Inject
  public MultisiteReplicationFetchFilter(
      SharedRefDatabaseWrapper sharedRefDb, GitRepositoryManager gitRepositoryManager) {
    this.sharedRefDb = sharedRefDb;
    this.gitRepositoryManager = gitRepositoryManager;
  }

  @Override
  public Set<String> filter(String projectName, Set<String> refs) {
    try (Repository repository =
        gitRepositoryManager.openRepository(Project.nameKey(projectName))) {
      RefDatabase refDb = repository.getRefDatabase();
      return refs.stream()
          .filter(
              ref -> {
                if (isRefAbsent(projectName, refDb, ref)) {
                  repLog.info(
                      "{}:{} is neither in the shared-refdb nor in the local repository"
                          + " thus will NOT BE fetched",
                      projectName,
                      ref);
                  return false;
                }
                return true;
              })
          .filter(
              ref -> {
                Optional<ObjectId> localRefOid =
                    getSha1IfUpToDateWithGlobalRefDb(repository, projectName, refDb, ref, true);
                localRefOid.ifPresent(
                    oid ->
                        repLog.info(
                            "{}:{}={} is already up-to-date with the shared-refdb and thus will NOT BE"
                                + " fetched",
                            projectName,
                            ref,
                            oid.getName()));

                return localRefOid.isEmpty();
              })
          .collect(Collectors.toSet());
    } catch (IOException ioe) {
      String message = String.format("Error while opening project: '%s'", projectName);
      repLog.error(message);
      logger.atSevere().withCause(ioe).log(message);
      return Collections.emptySet();
    }
  }

  private boolean isRefAbsent(String projectName, RefDatabase refDb, String ref) {
    try {
      return refDb.exactRef(ref) == null && absentInSharedRefDb(Project.nameKey(projectName), ref);
    } catch (GlobalRefDbLockException gle) {
      String message = String.format("%s is locked on shared-refdb", ref);
      repLog.error(message);
      logger.atSevere().withCause(gle).log(message);
      return false;
    } catch (IOException ioe) {
      String message =
          String.format("Error while extracting ref '%s' for project '%s'", ref, projectName);
      repLog.error(message);
      logger.atSevere().withCause(ioe).log(message);
      return false;
    }
  }

  private boolean absentInSharedRefDb(NameKey projectName, String ref) {
    return sharedRefDb
        .get(projectName, ref, String.class)
        .map(r -> ZERO_ID_NAME.equals(r))
        .orElse(true);
  }

  private Optional<ObjectId> getSha1IfUpToDateWithGlobalRefDb(
      Repository repository,
      String projectName,
      RefDatabase refDb,
      String ref,
      boolean retryWithRandomSleep) {
    try {
      Optional<ObjectId> localRefObjectId =
          Optional.ofNullable(refDb.exactRef(ref))
              .filter(r -> sharedRefDb.isUpToDate(Project.nameKey(projectName), r))
              .map(Ref::getObjectId);

      if (localRefObjectId.isEmpty() && retryWithRandomSleep) {
        randomSleepForMitigatingConditionWhereLocalRefHaveJustBeenChanged(
            projectName, localRefObjectId, ref);
        localRefObjectId =
            getSha1IfUpToDateWithGlobalRefDb(repository, projectName, refDb, ref, false);
      }

      return localRefObjectId;
    } catch (GlobalRefDbLockException gle) {
      String message = String.format("%s is locked on shared-refdb", ref);
      repLog.error(message);
      logger.atSevere().withCause(gle).log(message);
      return Optional.empty();
    } catch (IOException ioe) {
      String message =
          String.format("Error while extracting ref '%s' for project '%s'", ref, projectName);
      repLog.error(message);
      logger.atSevere().withCause(ioe).log(message);
      return Optional.empty();
    }
  }

  private void randomSleepForMitigatingConditionWhereLocalRefHaveJustBeenChanged(
      String projectName, Optional<ObjectId> refObjectId, String ref) {
    int randomSleepTimeMsec =
        MIN_WAIT_BEFORE_RELOAD_LOCAL_VERSION_MS
            + new Random().nextInt(RANDOM_WAIT_BEFORE_RELOAD_LOCAL_VERSION_MS);
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
      logger.atWarning().withCause(ie).log(message);
    }
  }
}
