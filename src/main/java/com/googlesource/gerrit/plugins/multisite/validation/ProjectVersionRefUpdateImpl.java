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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.gerritforge.gerrit.globalrefdb.GlobalRefDbSystemError;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.IntBlob;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

public class ProjectVersionRefUpdateImpl implements EventListener, ProjectVersionRefUpdate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Set<RefUpdate.Result> SUCCESSFUL_RESULTS =
      ImmutableSet.of(RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.NO_CHANGE);

  private final GitRepositoryManager gitRepositoryManager;
  private final GitReferenceUpdated gitReferenceUpdated;
  private final ProjectVersionLogger verLogger;
  private final String nodeInstanceId;

  protected final SharedRefDatabaseWrapper sharedRefDb;

  @Inject
  public ProjectVersionRefUpdateImpl(
      GitRepositoryManager gitRepositoryManager,
      SharedRefDatabaseWrapper sharedRefDb,
      GitReferenceUpdated gitReferenceUpdated,
      ProjectVersionLogger verLogger,
      @GerritInstanceId String nodeInstanceId) {
    this.gitRepositoryManager = gitRepositoryManager;
    this.sharedRefDb = sharedRefDb;
    this.gitReferenceUpdated = gitReferenceUpdated;
    this.verLogger = verLogger;
    this.nodeInstanceId = nodeInstanceId;
  }

  @Override
  public void onEvent(Event event) {
    logger.atFine().log("Processing event type: %s", event.type);
    // Producer of the Event use RefUpdatedEvent to trigger the version update
    if (nodeInstanceId.equals(event.instanceId) && event instanceof RefUpdatedEvent) {
      updateProducerProjectVersionUpdate((RefUpdatedEvent) event);
    }
  }

  private boolean isSpecialRefName(String refName) {
    return refName.startsWith(RefNames.REFS_SEQUENCES)
        || refName.startsWith(RefNames.REFS_STARRED_CHANGES)
        || refName.equals(MULTI_SITE_VERSIONING_REF);
  }

  private void updateProducerProjectVersionUpdate(RefUpdatedEvent refUpdatedEvent) {
    String refName = refUpdatedEvent.getRefName();

    if (isSpecialRefName(refName)) {
      logger.atFine().log(
          "Found a special ref name %s, skipping update for %s",
          refName, refUpdatedEvent.getProjectNameKey().get());
      return;
    }
    try {
      Project.NameKey projectNameKey = refUpdatedEvent.getProjectNameKey();
      long newVersion = getCurrentGlobalVersionNumber();

      Optional<RefUpdate> newProjectVersionRefUpdate =
          updateLocalProjectVersion(projectNameKey, newVersion);

      if (newProjectVersionRefUpdate.isPresent()) {
        verLogger.log(projectNameKey, newVersion, 0L);

        if (updateSharedProjectVersion(
            projectNameKey, newProjectVersionRefUpdate.get().getNewObjectId(), newVersion)) {
          gitReferenceUpdated.fire(projectNameKey, newProjectVersionRefUpdate.get(), null);
        }
      } else {
        logger.atWarning().log(
            "Ref %s not found on projet %s: skipping project version update",
            refUpdatedEvent.getRefName(), projectNameKey);
      }
    } catch (LocalProjectVersionUpdateException | SharedProjectVersionUpdateException e) {
      logger.atSevere().withCause(e).log(
          "Issue encountered when updating version for project %s",
          refUpdatedEvent.getProjectNameKey());
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
  private boolean updateSharedProjectVersion(
      Project.NameKey projectNameKey, ObjectId newObjectId, Long newVersion)
      throws SharedProjectVersionUpdateException {

    Ref sharedRef =
        sharedRefDb
            .get(projectNameKey, MULTI_SITE_VERSIONING_REF, String.class)
            .map(
                (String objectId) ->
                    new ObjectIdRef.Unpeeled(
                        Ref.Storage.NEW, MULTI_SITE_VERSIONING_REF, ObjectId.fromString(objectId)))
            .orElse(
                new ObjectIdRef.Unpeeled(
                    Ref.Storage.NEW, MULTI_SITE_VERSIONING_REF, ObjectId.zeroId()));
    Optional<Long> sharedVersion =
        sharedRefDb
            .get(projectNameKey, MULTI_SITE_VERSIONING_VALUE_REF, String.class)
            .map(Long::parseLong);

    try {
      if (sharedVersion.isPresent() && sharedVersion.get() >= newVersion) {
        logger.atWarning().log(
            "NOT Updating project %s version %s (value=%d) in shared ref-db because is more recent than the local one %s (value=%d) ",
            projectNameKey.get(),
            newObjectId,
            newVersion,
            sharedRef.getObjectId().getName(),
            sharedVersion.get());
        return false;
      }

      logger.atFine().log(
          "Updating shared project %s version to %s (value=%d)",
          projectNameKey.get(), newObjectId, newVersion);

      boolean success = sharedRefDb.compareAndPut(projectNameKey, sharedRef, newObjectId);
      if (!success) {
        String message =
            String.format(
                "Project version blob update failed for %s. Current value %s, new value: %s",
                projectNameKey.get(), safeGetObjectId(sharedRef), newObjectId);
        logger.atSevere().log(message);
        throw new SharedProjectVersionUpdateException(message);
      }

      success =
          sharedRefDb.compareAndPut(
              projectNameKey,
              MULTI_SITE_VERSIONING_VALUE_REF,
              sharedVersion.map(Object::toString).orElse(null),
              newVersion.toString());
      if (!success) {
        String message =
            String.format(
                "Project version update failed for %s. Current value %s, new value: %s",
                projectNameKey.get(), safeGetObjectId(sharedRef), newObjectId);
        logger.atSevere().log(message);
        throw new SharedProjectVersionUpdateException(message);
      }

      return true;
    } catch (GlobalRefDbSystemError refDbSystemError) {
      String message =
          String.format(
              "Error while updating shared project version for %s. Current value %s, new value: %s. Error: %s",
              projectNameKey.get(),
              sharedRef.getObjectId(),
              newObjectId,
              refDbSystemError.getMessage());
      logger.atSevere().withCause(refDbSystemError).log(message);
      throw new SharedProjectVersionUpdateException(message);
    }
  }

  /* (non-Javadoc)
   * @see com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate#getProjectLocalVersion(java.lang.String)
   */
  @Override
  public Optional<Long> getProjectLocalVersion(String projectName) {
    try (Repository repository =
        gitRepositoryManager.openRepository(Project.NameKey.parse(projectName))) {
      Optional<IntBlob> blob = IntBlob.parse(repository, MULTI_SITE_VERSIONING_REF);
      if (blob.isPresent()) {
        Long repoVersion = Integer.toUnsignedLong(blob.get().value());
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

  private Object safeGetObjectId(Ref currentRef) {
    return currentRef == null ? "null" : currentRef.getObjectId();
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
  private Optional<RefUpdate> updateLocalProjectVersion(
      Project.NameKey projectNameKey, long newVersionNumber)
      throws LocalProjectVersionUpdateException {
    logger.atFine().log(
        "Updating local version for project %s with version %d",
        projectNameKey.get(), newVersionNumber);
    try (Repository repository = gitRepositoryManager.openRepository(projectNameKey)) {
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

      return Optional.of(refUpdate);
    } catch (IOException e) {
      String message = "Cannot create versioning command for " + projectNameKey.get();
      logger.atSevere().withCause(e).log(message);
      throw new LocalProjectVersionUpdateException(message);
    }
  }

  private long getCurrentGlobalVersionNumber() {
    return System.currentTimeMillis() / 1000;
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
