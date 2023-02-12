// Copyright (C) 2019 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.forwarder.router;

import static com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation.DELETE;
import static com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation.INDEX;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.RefEvent;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexAccountHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexChangeHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexGroupHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexProjectHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.IndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public class IndexEventRouter
    implements ForwardedEventRouter<IndexEvent>, EventListener, LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ForwardedIndexAccountHandler indexAccountHandler;
  private final ForwardedIndexChangeHandler indexChangeHandler;
  private final ForwardedIndexGroupHandler indexGroupHandler;
  private final ForwardedIndexProjectHandler indexProjectHandler;
  private final AllUsersName allUsersName;
  private final String gerritInstanceId;

  @Inject
  public IndexEventRouter(
      ForwardedIndexAccountHandler indexAccountHandler,
      ForwardedIndexChangeHandler indexChangeHandler,
      ForwardedIndexGroupHandler indexGroupHandler,
      ForwardedIndexProjectHandler indexProjectHandler,
      AllUsersName allUsersName,
      @GerritInstanceId String gerritInstanceId) {
    this.indexAccountHandler = indexAccountHandler;
    this.indexChangeHandler = indexChangeHandler;
    this.indexGroupHandler = indexGroupHandler;
    this.indexProjectHandler = indexProjectHandler;
    this.allUsersName = allUsersName;
    this.gerritInstanceId = gerritInstanceId;
  }

  @Override
  public void route(IndexEvent sourceEvent) throws IOException {
    if (sourceEvent instanceof ChangeIndexEvent) {
      ChangeIndexEvent changeIndexEvent = (ChangeIndexEvent) sourceEvent;
      ForwardedIndexingHandler.Operation operation = changeIndexEvent.deleted ? DELETE : INDEX;
      indexChangeHandler.index(
          changeIndexEvent.projectName + "~" + changeIndexEvent.changeId,
          operation,
          Optional.of(changeIndexEvent));
    } else if (sourceEvent instanceof AccountIndexEvent) {
      AccountIndexEvent accountIndexEvent = (AccountIndexEvent) sourceEvent;
      indexAccountHandler.indexAsync(Account.id(accountIndexEvent.accountId), INDEX);
    } else if (sourceEvent instanceof GroupIndexEvent) {
      GroupIndexEvent groupIndexEvent = (GroupIndexEvent) sourceEvent;
      indexGroupHandler.index(groupIndexEvent.groupUUID, INDEX, Optional.of(groupIndexEvent));
    } else if (sourceEvent instanceof ProjectIndexEvent) {
      ProjectIndexEvent projectIndexEvent = (ProjectIndexEvent) sourceEvent;
      indexProjectHandler.index(
          projectIndexEvent.projectName, INDEX, Optional.of(projectIndexEvent));
    } else {
      throw new UnsupportedOperationException(
          String.format("Cannot route event %s", sourceEvent.getType()));
    }
  }

  public void onRefReplicated(RefEvent replicationEvent) throws IOException {
    if (replicationEvent.getProjectNameKey().equals(allUsersName)) {
      Account.Id accountId = Account.Id.fromRef(replicationEvent.getRefName());
      if (accountId != null) {
        indexAccountHandler.index(accountId, INDEX, Optional.empty());
      } else {
        indexAccountHandler.doAsyncIndex();
      }
    }
  }

  @Override
  public void onEvent(Event event) {
    if (event instanceof RefEvent
        && (event.getType().contains("fetch-ref-replicated")
            || event.getType().contains("fetch-ref-replication-done"))
        && gerritInstanceId.equals(event.instanceId)) {
      try {
        onRefReplicated((RefEvent) event);
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("Error while processing event %s", event);
      }
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    Set<Account.Id> accountsToIndex = indexAccountHandler.pendingAccountsToIndex();
    if (!accountsToIndex.isEmpty()) {
      logger.atWarning().log("Forcing reindex of accounts %s upon shutdown", accountsToIndex);
      indexAccountHandler.doAsyncIndex();
    }

    Set<Account.Id> accountsIndexFailed = indexAccountHandler.pendingAccountsToIndex();
    if (!accountsIndexFailed.isEmpty()) {
      logger.atSevere().log(
          "The accounts %s failed to be indexed and their Lucene index is stale",
          accountsIndexFailed);
    }
  }
}
