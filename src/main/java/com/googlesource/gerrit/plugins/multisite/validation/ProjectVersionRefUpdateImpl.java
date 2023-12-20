// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.PLUGIN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.gerritforge.gerrit.globalrefdb.GlobalRefDbSystemError;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.events.GitBatchRefUpdateListener;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class ProjectVersionRefUpdateImpl
    implements GitBatchRefUpdateListener, ProjectVersionRefUpdate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Set<RefUpdate.Result> SUCCESSFUL_RESULTS =
      ImmutableSet.of(RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.NO_CHANGE);

  private final GitRepositoryManager gitRepositoryManager;
  private final GitReferenceUpdated gitReferenceUpdated;
  private final ProjectVersionLogger verLogger;

  protected final SharedRefDatabaseWrapper sharedRefDb;

  @Inject
  public ProjectVersionRefUpdateImpl(
      GitRepositoryManager gitRepositoryManager,
      SharedRefDatabaseWrapper sharedRefDb,
      GitReferenceUpdated gitReferenceUpdated,
      ProjectVersionLogger verLogger) {
    this.gitRepositoryManager = gitRepositoryManager;
    this.sharedRefDb = sharedRefDb;
    this.gitReferenceUpdated = gitReferenceUpdated;
    this.verLogger = verLogger;
  }

  @Override
  public void onGitBatchRefUpdate(Event event) {
    // Producer of the Event use RefUpdatedEvent to trigger the version update
    updateProducerProjectVersionUpdate(event);
  }

  private void updateProducerProjectVersionUpdate(Event refUpdatedEvent) {
    if (refUpdatedEvent.getRefNames().stream()
        .allMatch(refName -> refName.equals(MULTI_SITE_VERSIONING_REF))) {
      logger.atFine().log(
          "Found a special ref name %s, skipping update for %s",
          MULTI_SITE_VERSIONING_REF, refUpdatedEvent.getProjectName());
      return;
    }

    try {
      Project.NameKey projectNameKey = Project.nameKey(refUpdatedEvent.getProjectName());
      long newVersion = getCurrentGlobalVersionNumber();

      RefUpdate newProjectVersionRefUpdate = updateLocalProjectVersion(projectNameKey, newVersion);

      verLogger.log(projectNameKey, newVersion, 0L);

      if (updateSharedProjectVersion(projectNameKey, newVersion)) {
        gitReferenceUpdated.fire(projectNameKey, newProjectVersionRefUpdate, null);
      }
    } catch (LocalProjectVersionUpdateException | SharedProjectVersionUpdateException e) {
      logger.atSevere().withCause(e).log(
          "Issue encountered when updating version for project %s",
          refUpdatedEvent.getProjectName());
    }
  }

  private RefUpdate getProjectVersionRefUpdate(Repository repository, Long version)
      throws IOException {
    RefUpdate refUpdate = repository.getRefDatabase().newUpdate(MULTI_SITE_VERSIONING_REF, false);
    refUpdate.setNewObjectId(getNewId(repository, version));
    refUpdate.setForceUpdate(true);
    return refUpdate;
  }

  private ObjectId getNewId(Repository repository, Long version) throws IOException {
    ObjectInserter ins = repository.newObjectInserter();
    ObjectId newId = ins.insert(OBJ_BLOB, Long.toString(version).getBytes(UTF_8));
    ins.flush();
    return newId;
  }

  @SuppressWarnings("FloggerLogString")
  private boolean updateSharedProjectVersion(Project.NameKey projectNameKey, Long newVersion)
      throws SharedProjectVersionUpdateException {

    Optional<Long> sharedVersion =
        sharedRefDb
            .get(projectNameKey, MULTI_SITE_VERSIONING_VALUE_REF, String.class)
            .map(Long::parseLong);

    try {
      if (sharedVersion.isPresent() && sharedVersion.get() >= newVersion) {
        logger.atWarning().log(
            "NOT Updating project %s value=%d in shared ref-db because is more recent than the local value=%d",
            projectNameKey.get(), newVersion, sharedVersion.get());
        return false;
      }

      logger.atFine().log(
          "Updating shared project %s value to %d", projectNameKey.get(), newVersion);

      updateProjectVersionValue(projectNameKey, newVersion, sharedVersion);
      return true;
    } catch (GlobalRefDbSystemError refDbSystemError) {
      String message =
          String.format(
              "Error while updating shared project value for %s. Current value %s, new value: %s. Error: %s",
              projectNameKey.get(),
              sharedVersion.map(Object::toString).orElse(null),
              newVersion,
              refDbSystemError.getMessage());
      logger.atSevere().withCause(refDbSystemError).log(message);
      throw new SharedProjectVersionUpdateException(message);
    }
  }

  private void updateProjectVersionValue(
      NameKey projectNameKey, Long newVersion, Optional<Long> sharedVersion) {
    try {
      if (sharedRefDb.isSetOperationSupported()) {
        sharedRefDb.put(projectNameKey, MULTI_SITE_VERSIONING_VALUE_REF, newVersion.toString());
        return;
      }
    } catch (NoSuchMethodError e) {
      logger.atSevere().log(
          "Global-refdb library is outdated and is not supporting "
              + "'put' method, update global-refdb to the newest version. Falling back to 'compareAndPut'");
    }

    sharedRefDb.compareAndPut(
        projectNameKey,
        MULTI_SITE_VERSIONING_VALUE_REF,
        sharedVersion.map(Object::toString).orElse(null),
        newVersion.toString());
  }

  /* (non-Javadoc)
   * @see com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate#getProjectLocalVersion(java.lang.String)
   */
  @Override
  public Optional<Long> getProjectLocalVersion(String projectName) {
    try (Repository repository =
        gitRepositoryManager.openRepository(Project.NameKey.parse(projectName))) {
      Optional<Long> blob = longBlobParse(repository, MULTI_SITE_VERSIONING_REF);
      if (blob.isPresent()) {
        Long repoVersion = blob.get();
        logger.atFine().log("Local project '%s' has version %d", projectName, repoVersion);
        return Optional.of(repoVersion);
      }
    } catch (RepositoryNotFoundException re) {
      logger.atFine().log("Project '%s' not found", projectName);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot read local project '%s' version", projectName);
    }
    return Optional.empty();
  }

  private Optional<Long> longBlobParse(Repository repo, String refName) throws IOException {
    return Optional.ofNullable(repo.exactRef(refName))
        .map(
            (ref) -> {
              try {
                return Long.parseLong(
                    new String(repo.open(ref.getObjectId()).getBytes(), StandardCharsets.UTF_8));
              } catch (IOException e) {
                logger.atSevere().withCause(e).log(
                    "Unable to extract long BLOB from %s:%s", repo.getDirectory(), ref);
                return null;
              }
            })
        .filter(Predicates.notNull());
  }

  /* (non-Javadoc)
   * @see com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate#getProjectRemoteVersion(java.lang.String)
   */
  @Override
  public Optional<Long> getProjectRemoteVersion(String projectName) {
    Optional<String> globalVersion =
        sharedRefDb.get(
            Project.NameKey.parse(projectName), MULTI_SITE_VERSIONING_VALUE_REF, String.class);
    return globalVersion.flatMap(longString -> getLongValueOf(longString));
  }

  private Optional<Long> getLongValueOf(String longString) {
    try {
      return Optional.ofNullable(Long.parseLong(longString));
    } catch (NumberFormatException e) {
      logger.atSevere().withCause(e).log(
          "Unable to parse timestamp value %s into Long", longString);
      return Optional.empty();
    }
  }

  @SuppressWarnings("FloggerLogString")
  private RefUpdate updateLocalProjectVersion(Project.NameKey projectNameKey, long newVersionNumber)
      throws LocalProjectVersionUpdateException {
    logger.atFine().log(
        "Updating local version for project %s with version %d",
        projectNameKey.get(), newVersionNumber);
    try (RefUpdateContext ctx = RefUpdateContext.open(PLUGIN);
        Repository repository = gitRepositoryManager.openRepository(projectNameKey)) {
      RefUpdate refUpdate = getProjectVersionRefUpdate(repository, newVersionNumber);
      RefUpdate.Result result = refUpdate.update();
      if (!isSuccessful(result)) {
        String message =
            String.format(
                "RefUpdate failed with result %s for: project=%s, version=%d",
                result.name(), projectNameKey.get(), newVersionNumber);
        logger.atSevere().log(message);
        throw new LocalProjectVersionUpdateException(message);
      }

      return refUpdate;
    } catch (IOException e) {
      String message = "Cannot create versioning command for " + projectNameKey.get();
      logger.atSevere().withCause(e).log(message);
      throw new LocalProjectVersionUpdateException(message);
    }
  }

  private long getCurrentGlobalVersionNumber() {
    return System.currentTimeMillis();
  }

  private Boolean isSuccessful(RefUpdate.Result result) {
    return SUCCESSFUL_RESULTS.contains(result);
  }

  public static class LocalProjectVersionUpdateException extends Exception {
    private static final long serialVersionUID = 7649956232401457023L;

    public LocalProjectVersionUpdateException(String projectName) {
      super("Cannot update local project version of " + projectName);
    }
  }

  public static class SharedProjectVersionUpdateException extends Exception {
    private static final long serialVersionUID = -9153858177700286314L;

    public SharedProjectVersionUpdateException(String projectName) {
      super("Cannot update shared project version of " + projectName);
    }
  }
}
