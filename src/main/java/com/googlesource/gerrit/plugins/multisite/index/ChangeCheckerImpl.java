// Copyright (C) 2018 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.index;

import com.google.gerrit.entities.Change;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeCheckerImpl implements ChangeChecker {
  private static final Logger log = LoggerFactory.getLogger(ChangeCheckerImpl.class);
  private final GitRepositoryManager gitRepoMgr;
  private final OneOffRequestContext oneOffReqCtx;
  private final String changeId;
  private final ChangeFinder changeFinder;
  private final String instanceId;
  private Optional<Long> computedChangeTs = Optional.empty();
  private Optional<ChangeNotes> changeNotes = Optional.empty();

  public interface Factory {
    public ChangeChecker create(String changeId);
  }

  public static Module module() {
    return new FactoryModuleBuilder()
        .implement(ChangeChecker.class, ChangeCheckerImpl.class)
        .build(ChangeCheckerImpl.Factory.class);
  }

  @Inject
  public ChangeCheckerImpl(
      GitRepositoryManager gitRepoMgr,
      ChangeFinder changeFinder,
      OneOffRequestContext oneOffReqCtx,
      @GerritInstanceId String instanceId,
      @GerritServerConfig Config config,
      @Assisted String changeId) {
    this.changeFinder = changeFinder;
    this.gitRepoMgr = gitRepoMgr;
    this.oneOffReqCtx = oneOffReqCtx;
    this.changeId = changeId;
    this.instanceId = instanceId;
  }

  @Override
  public Optional<ChangeIndexEvent> newIndexEvent(String projectName, int changeId, boolean deleted)
      throws IOException {
    return getComputedChangeTs()
        .map(
            ts -> {
              ChangeIndexEvent event =
                  new ChangeIndexEvent(projectName, changeId, deleted, instanceId);
              event.eventCreatedOn = ts;
              event.targetSha = getBranchTargetSha();
              return event;
            });
  }

  @Override
  public Optional<ChangeNotes> getChangeNotes() {
    try (ManualRequestContext ctx = oneOffReqCtx.open()) {
      this.changeNotes = changeFinder.findOne(changeId);
      return changeNotes;
    }
  }

  @Override
  public boolean isUpToDate(Optional<ChangeIndexEvent> indexEvent) {
    getComputedChangeTs();
    if (!computedChangeTs.isPresent()) {
      log.warn("Unable to compute last updated ts for change {}", changeId);
      return true;
    }

    if (indexEvent.isPresent() && indexEvent.get().targetSha == null) {
      return indexEvent.map(e -> (computedChangeTs.get() >= e.eventCreatedOn)).orElse(true);
    }

    return indexEvent
        .map(
            e ->
                (computedChangeTs.get() > e.eventCreatedOn)
                    || ((computedChangeTs.get() == e.eventCreatedOn) && repositoryHas(e.targetSha)))
        .orElse(true);
  }

  @Override
  public Optional<Long> getComputedChangeTs() {
    if (!computedChangeTs.isPresent()) {
      computedChangeTs = computeLastChangeTs();
    }
    return computedChangeTs;
  }

  @Override
  public String toString() {
    return "change-id="
        + changeId
        + "@"
        + getComputedChangeTs().map(ChangeIndexEvent::format)
        + "/"
        + getBranchTargetSha();
  }

  private String getBranchTargetSha() {
    try {
      try (Repository repo = gitRepoMgr.openRepository(changeNotes.get().getProjectName())) {
        String refName = changeNotes.get().getChange().getDest().branch();
        Ref ref = repo.exactRef(refName);
        if (ref == null) {
          log.warn("Unable to find target ref {} for change {}", refName, changeId);
          return null;
        }
        return ref.getTarget().getObjectId().getName();
      }
    } catch (IOException e) {
      log.warn("Unable to resolve target branch SHA for change {}", changeId, e);
      return null;
    }
  }

  private boolean repositoryHas(String targetSha) {
    try (Repository repo = gitRepoMgr.openRepository(changeNotes.get().getProjectName())) {
      return repo.parseCommit(ObjectId.fromString(targetSha)) != null;
    } catch (IOException e) {
      log.warn("Unable to find SHA1 {} for change {}", targetSha, changeId, e);
      return false;
    }
  }

  @Override
  public boolean isChangeConsistent() {
    Optional<ChangeNotes> notes = getChangeNotes();
    if (!notes.isPresent()) {
      log.warn("Unable to compute change notes for change {}", changeId);
      return true;
    }
    ObjectId currentPatchSetCommitId = notes.get().getCurrentPatchSet().commitId();
    try (Repository repo = gitRepoMgr.openRepository(changeNotes.get().getProjectName());
        RevWalk walk = new RevWalk(repo)) {
      walk.parseCommit(currentPatchSetCommitId);
    } catch (StorageException | MissingObjectException e) {
      log.warn(
          String.format(
              "Consistency check failed for change %s, missing current patchset commit %s",
              changeId, currentPatchSetCommitId.getName()),
          e);
      return false;
    } catch (IOException e) {
      log.warn(
          String.format(
              "Cannot check consistency for change %s, current patchset commit %s. Assuming change"
                  + " is consistent",
              changeId, currentPatchSetCommitId.getName()),
          e);
    }
    return true;
  }

  private Optional<Long> computeLastChangeTs() {
    return getChangeNotes().map(this::getTsFromChange);
  }

  private long getTsFromChange(ChangeNotes notes) {
    Change change = notes.getChange();
    Timestamp changeTs = Timestamp.from(change.getLastUpdatedOn());
    return changeTs.getTime() / 1000;
  }
}
