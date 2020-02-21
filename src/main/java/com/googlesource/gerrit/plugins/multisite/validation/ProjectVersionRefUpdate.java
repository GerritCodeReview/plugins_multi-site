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

package com.googlesource.gerrit.plugins.multisite.validation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.gerritforge.gerrit.globalrefdb.GlobalRefDbSystemError;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.IntBlob;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.replication.RefReplicationDoneEvent;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class ProjectVersionRefUpdate implements EventListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String MULTI_SITE_VERSIONING_REF = "refs/multi-site/version";
  public static final String SEQUENCE_REF_PREFIX = "refs/sequences/";
  private static final Ref NULL_PROJECT_VERSION_REF =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, MULTI_SITE_VERSIONING_REF, ObjectId.zeroId());
  private static final Set<RefUpdate.Result> SUCCESSFUL_RESULTS =
      ImmutableSet.of(RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.NO_CHANGE);

  protected final SharedRefDatabaseWrapper sharedRefDb;
  private final GitRepositoryManager gitRepositoryManager;

  @Inject
  public ProjectVersionRefUpdate(
      GitRepositoryManager gitRepositoryManager, SharedRefDatabaseWrapper sharedRefDb) {
    this.gitRepositoryManager = gitRepositoryManager;
    this.sharedRefDb = sharedRefDb;
  }

  @Override
  public void onEvent(Event event) {
    logger.atFine().log("Processing event type: " + event.type);
    // Producer of the Event use RefUpdatedEvent to trigger the version update
    if (!Context.isForwardedEvent() && event instanceof RefUpdatedEvent) {
      updateProducerProjectVersionUpdate((RefUpdatedEvent) event);
    }

    // Consumers of the Event use RefReplicationDoneEvent to trigger the version update
    if (Context.isForwardedEvent() && event instanceof RefReplicationDoneEvent) {
      updateConsumerProjectVersion((RefReplicationDoneEvent) event);
    }
  }

  private void updateConsumerProjectVersion(RefReplicationDoneEvent refReplicationDoneEvent) {
    Project.NameKey projectNameKey = refReplicationDoneEvent.getProjectNameKey();

    if (refReplicationDoneEvent.getRefName().startsWith(SEQUENCE_REF_PREFIX)) {
      logger.atFine().log("Found Sequence ref, skipping update for " + projectNameKey.get());
      return;
    }
    try {
      updateLocalProjectVersion(projectNameKey, refReplicationDoneEvent.getRefName());
    } catch (LocalProjectVersionUpdateException e) {
      logger.atSevere().withCause(e).log(
          "Issue encountered when updating version for project " + projectNameKey);
    }
  }

  private void updateProducerProjectVersionUpdate(RefUpdatedEvent refUpdatedEvent) {
    if (refUpdatedEvent.getRefName().startsWith(SEQUENCE_REF_PREFIX)) {
      logger.atFine().log(
          "Found Sequence ref, skipping update for " + refUpdatedEvent.getProjectNameKey().get());
      return;
    }
    try {
      Project.NameKey projectNameKey = refUpdatedEvent.getProjectNameKey();
      Ref currentProjectVersionRef = getLocalProjectVersionRef(refUpdatedEvent.getProjectNameKey());
      Optional<ObjectId> newProjectVersionObjectId =
          updateLocalProjectVersion(projectNameKey, refUpdatedEvent.getRefName());

      if (newProjectVersionObjectId.isPresent()) {
        updateSharedProjectVersion(
            projectNameKey, currentProjectVersionRef, newProjectVersionObjectId.get());
      } else {
        logger.atWarning().log(
            "Ref %s not found on projet %s: skipping project version update",
            refUpdatedEvent.getRefName(), projectNameKey);
      }
    } catch (LocalProjectVersionUpdateException | SharedProjectVersionUpdateException e) {
      logger.atSevere().withCause(e).log(
          "Issue encountered when updating version for project "
              + refUpdatedEvent.getProjectNameKey());
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

  private Ref getLocalProjectVersionRef(Project.NameKey projectNameKey)
      throws LocalProjectVersionUpdateException {
    try (Repository repository = gitRepositoryManager.openRepository(projectNameKey)) {
      Ref ref = repository.findRef(MULTI_SITE_VERSIONING_REF);
      return ref != null ? ref : NULL_PROJECT_VERSION_REF;
    } catch (IOException e) {
      String message =
          String.format("Error while getting current version for %s", projectNameKey.get());
      logger.atSevere().withCause(e).log(message);
      throw new LocalProjectVersionUpdateException(message);
    }
  }

  private Optional<Long> getLastRefUpdatedTimestamp(Project.NameKey projectNameKey, String refName)
      throws LocalProjectVersionUpdateException {
    logger.atFine().log(
        String.format(
            "Getting last ref updated time for project %s, ref %s", projectNameKey.get(), refName));
    try (Repository repository = gitRepositoryManager.openRepository(projectNameKey)) {
      Ref ref = repository.findRef(refName);
      if (ref == null) {
        logger.atWarning().log("Unable to find ref " + refName + " in project " + projectNameKey);
        return Optional.empty();
      }
      try (RevWalk walk = new RevWalk(repository)) {
        RevCommit commit = walk.parseCommit(ref.getObjectId());
        return Optional.of(Integer.toUnsignedLong(commit.getCommitTime()));
      }
    } catch (IOException ioe) {
      String message =
          String.format(
              "Error while getting last ref updated time for project %s, ref %s",
              projectNameKey.get(), refName);
      logger.atSevere().withCause(ioe).log(message);
      throw new LocalProjectVersionUpdateException(message);
    }
  }

  private void updateSharedProjectVersion(
      Project.NameKey projectNameKey, Ref currentRef, ObjectId newObjectId)
      throws SharedProjectVersionUpdateException {
    logger.atFine().log(
        String.format(
            "Updating shared project version for %s. Current value %s, new value: %s",
            projectNameKey.get(), currentRef.getObjectId(), newObjectId));
    try {
      boolean success = sharedRefDb.compareAndPut(projectNameKey, currentRef, newObjectId);
      String message =
          String.format(
              "Project version update failed for %s. Current value %s, new value: %s",
              projectNameKey.get(), currentRef.getObjectId(), newObjectId);
      if (!success) {
        logger.atSevere().log(message);
        throw new SharedProjectVersionUpdateException(message);
      }
    } catch (GlobalRefDbSystemError refDbSystemError) {
      String message =
          String.format(
              "Error while updating shared project version for %s. Current value %s, new value: %s. Error: %s",
              projectNameKey.get(),
              currentRef.getObjectId(),
              newObjectId,
              refDbSystemError.getMessage());
      logger.atSevere().withCause(refDbSystemError).log(message);
      throw new SharedProjectVersionUpdateException(message);
    }
  }

  public Optional<Long> getProjectLocalVersion(String projectName) {
    try (Repository repository =
        gitRepositoryManager.openRepository(Project.NameKey.parse(projectName))) {
      Optional<IntBlob> blob = IntBlob.parse(repository, MULTI_SITE_VERSIONING_REF);
      if (blob.isPresent()) {
        Long repoVersion = Integer.toUnsignedLong(blob.get().value());
        logger.atFine().log("Local project '%s' has version %d", projectName, repoVersion);
        return Optional.of(repoVersion);
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot read local project '%s' version", projectName);
    }
    return Optional.empty();
  }

  public Optional<Long> getProjectRemoteVersion(String projectName) {
    Optional<ObjectId> remoteObjectId =
        sharedRefDb.get(
            Project.NameKey.parse(projectName), MULTI_SITE_VERSIONING_REF, ObjectId.class);
    if (remoteObjectId.isPresent()) {
      return getLongFromObjectId(projectName, remoteObjectId.get());
    } else {
      logger.atFine().log("Didn't find remote version for %s", projectName);
      return Optional.empty();
    }
  }

  private Optional<Long> getLongFromObjectId(String projectName, ObjectId objectId) {
    try (Repository repository =
        gitRepositoryManager.openRepository(Project.NameKey.parse(projectName))) {
      ObjectReader or = repository.newObjectReader();
      ObjectLoader ol = or.open(objectId, OBJ_BLOB);
      if (ol.getType() != OBJ_BLOB) {
        // In theory this should be thrown by open but not all implementations may do it properly
        // (certainly InMemoryRepository doesn't).
        logger.atSevere().log("Incorrect object type loaded for objectId %s", objectId.toString());
        return Optional.empty();
      }
      String str = CharMatcher.whitespace().trimFrom(new String(ol.getCachedBytes(), UTF_8));
      Integer value = Ints.tryParse(str);
      logger.atInfo().log(
          "Found remote version for project %s, value: %s - %d",
          projectName, objectId.toString(), value);
      return Optional.of(Integer.toUnsignedLong(value));
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot parse objectId %s", objectId.toString());
      return Optional.empty();
    }
  }

  private Optional<ObjectId> updateLocalProjectVersion(
      Project.NameKey projectNameKey, String refName) throws LocalProjectVersionUpdateException {
    Optional<Long> lastRefUpdatedTimestamp = getLastRefUpdatedTimestamp(projectNameKey, refName);
    if (!lastRefUpdatedTimestamp.isPresent()) {
      return Optional.empty();
    }

    logger.atFine().log("Updating local version for project " + projectNameKey.get());
    try (Repository repository = gitRepositoryManager.openRepository(projectNameKey)) {
      RefUpdate refUpdate = getProjectVersionRefUpdate(repository, lastRefUpdatedTimestamp.get());
      RefUpdate.Result result = refUpdate.update();
      if (!isSuccessful(result)) {
        String message =
            String.format(
                "RefUpdate failed with result %s for: project=%s, version=%d",
                result.name(), projectNameKey.get(), lastRefUpdatedTimestamp.get());
        logger.atSevere().log(message);
        throw new LocalProjectVersionUpdateException(message);
      }
      return Optional.of(refUpdate.getNewObjectId());
    } catch (IOException e) {
      String message = "Cannot create versioning command for " + projectNameKey.get();
      logger.atSevere().withCause(e).log(message);
      throw new LocalProjectVersionUpdateException(message);
    }
  }

  private Boolean isSuccessful(RefUpdate.Result result) {
    return SUCCESSFUL_RESULTS.contains(result);
  }

  public static class LocalProjectVersionUpdateException extends Exception {
    public LocalProjectVersionUpdateException(String projectName) {
      super("Cannot update local project version of " + projectName);
    }
  }

  public static class SharedProjectVersionUpdateException extends Exception {
    public SharedProjectVersionUpdateException(String projectName) {
      super("Cannot update shared project version of " + projectName);
    }
  }
}
