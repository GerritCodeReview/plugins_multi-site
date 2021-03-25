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

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.index.ForwardedIndexExecutor;
import com.googlesource.gerrit.plugins.multisite.index.GroupChecker;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Index a group using {@link GroupIndexer}. This class is meant to be used on the receiving side of
 * the {@link IndexEventForwarder} since it will prevent indexed group to be forwarded again causing
 * an infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same group uuid
 */
@Singleton
public class ForwardedIndexGroupHandler
    extends ForwardedIndexingHandlerWithRetries<String, GroupIndexEvent> {
  private final GroupIndexer indexer;
  private final GroupChecker groupChecker;

  @Inject
  ForwardedIndexGroupHandler(
      GroupIndexer indexer,
      Configuration config,
      GroupChecker groupChecker,
      OneOffRequestContext oneOffRequestContext,
      @ForwardedIndexExecutor ScheduledExecutorService indexExecutor) {
    super(indexExecutor, config, oneOffRequestContext);
    this.indexer = indexer;
    this.groupChecker = groupChecker;
  }

  @Override
  protected void doIndex(String uuid, Optional<GroupIndexEvent> event) {
    attemptToIndex(uuid, event, 0);
  }

  @Override
  protected void reindex(String id) {
    indexer.index(AccountGroup.uuid(id));
  }

  @Override
  protected String indexName() {
    return "group";
  }

  @Override
  protected void attemptToIndex(
      String uuid, Optional<GroupIndexEvent> groupIndexEvent, int retryCount) {
    reindexAndCheckIsUpToDate(uuid, groupIndexEvent, groupChecker, retryCount);
  }

  @Override
  protected void doDelete(String uuid, Optional<GroupIndexEvent> event) {
    throw new UnsupportedOperationException("Delete from group index not supported");
  }
}
