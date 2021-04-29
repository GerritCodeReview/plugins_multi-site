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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.index.ForwardedIndexExecutor;
import com.googlesource.gerrit.plugins.multisite.index.GroupChecker;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Index a group using {@link GroupIndexer}. This class is meant to be used on the receiving side of
 * the {@link IndexEventForwarder} since it will prevent indexed group to be forwarded again causing
 * an infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same group uuid
 */
@Singleton
public class ForwardedIndexGroupHandler extends ForwardedIndexingHandler<String, GroupIndexEvent> {
  private final GroupIndexer indexer;
  private final GroupChecker groupChecker;
  private final ScheduledExecutorService indexExecutor;
  private final int retryInterval;
  private final int maxTries;

  @Inject
  ForwardedIndexGroupHandler(
      GroupIndexer indexer,
      Configuration config,
      GroupChecker groupChecker,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor) {
    super(config.index().numStripedLocks());
    this.indexer = indexer;
    this.groupChecker = groupChecker;
    this.indexExecutor = indexExecutor;
    Configuration.Index indexConfig = config.index();
    this.retryInterval = indexConfig != null ? indexConfig.retryInterval() : 0;
    this.maxTries = indexConfig != null ? indexConfig.maxTries() : 0;
  }

  @Override
  protected void doIndex(String uuid, Optional<GroupIndexEvent> event) {
    doIndex(uuid, event, 0);
  }

  protected void doIndex(String uuid, Optional<GroupIndexEvent> groupIndexEvent, int retryCount) {
    indexer.index(new AccountGroup.UUID(uuid));
    if (groupChecker.isGroupUpToDate(groupIndexEvent)) {
      if (retryCount > 0) {
        log.warn("Group '{}' has been eventually indexed after {} attempt(s)", uuid, retryCount);
      } else {
        log.debug("Group '{}' successfully indexed", uuid);
      }
    } else {
      log.debug("Group '{}' rescheduling indexing", uuid);
      rescheduleIndex(uuid, groupIndexEvent, retryCount + 1);
    }
  }

  private boolean rescheduleIndex(
      String uuid, Optional<GroupIndexEvent> indexEvent, int retryCount) {
    if (retryCount > maxTries) {
      log.error(
          "Group '{}' could not be indexed after {} retries. Group index could be stale.",
          uuid,
          retryCount);
      return false;
    }

    log.warn(
        "Retrying for the #{} time to index Group {} after {} msecs",
        retryCount,
        uuid,
        retryInterval);
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        indexExecutor.schedule(
            () -> {
              try {
                Context.setForwardedEvent(true);
                doIndex(uuid, indexEvent, retryCount);
              } catch (Exception e) {
                log.warn("Group {} could not be indexed", uuid, e);
              }
            },
            retryInterval,
            TimeUnit.MILLISECONDS);
    return true;
  }

  @Override
  protected void doDelete(String uuid, Optional<GroupIndexEvent> event) {
    throw new UnsupportedOperationException("Delete from group index not supported");
  }
}
