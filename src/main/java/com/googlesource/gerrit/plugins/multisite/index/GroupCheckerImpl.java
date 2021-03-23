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

package com.googlesource.gerrit.plugins.multisite.index;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
class GroupCheckerImpl implements GroupChecker {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;

  @Inject
  GroupCheckerImpl(GitRepositoryManager repoManager, AllUsersName allUsers) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
  }

  @Override
  public boolean isGroupUpToDate(Optional<GroupIndexEvent> groupIndexEvent) {
    if (!groupIndexEvent.isPresent()) {
      logger.atWarning().log("Group Index empty, considering this group up-to-date");
      return true;
    }
    GroupIndexEvent event = groupIndexEvent.get();
    AccountGroup.UUID groupUUID = new AccountGroup.UUID(event.groupUUID);

    if (event.sha1 == null) {
      logger.atWarning().log(
          "Event for group '%s' does not contain sha1, consider group up-to-date for compatibility.",
          groupUUID);
      return true;
    }

    try (Repository repo = repoManager.openRepository(allUsers)) {
      if (commitExistsInRepo(repo, event.sha1)) {
        logger.atInfo().log(
            "Group '%s' up-to-date: sha1 '%s' exists in All-Users", groupUUID, event.sha1);
        return true;
      } else {
        logger.atWarning().log(
            "Group '%s' NOT up-to-date: sha1 '%s' still missing in All-Users",
            groupUUID, event.sha1);
      }
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Could not check whether Group '%s' is up-to-date", groupUUID);
    }
    return false;
  }

  @Override
  public ObjectId getGroupHead(String groupUUID) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return Optional.ofNullable(
              repo.exactRef(RefNames.refsGroups(new AccountGroup.UUID(groupUUID))))
          .map(Ref::getObjectId)
          .orElse(ObjectId.zeroId());
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Fatal: could not get head of group %s.", groupUUID);
      return ObjectId.zeroId();
    }
  }

  @VisibleForTesting
  boolean commitExistsInRepo(Repository repo, ObjectId sha1) throws IOException {
    try (RevWalk revWalk = new RevWalk(repo)) {
      revWalk.parseCommit(sha1);
      return true;
    } catch (MissingObjectException e) {
      logger.atWarning().log("Commit %s does not exist in All-Users", sha1);
    }
    return false;
  }
}
