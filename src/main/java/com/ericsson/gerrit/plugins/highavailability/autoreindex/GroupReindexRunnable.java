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

package com.ericsson.gerrit.plugins.highavailability.autoreindex;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexGroupHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.google.common.collect.Streams;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.Id;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupReindexRunnable extends ReindexRunnable<AccountGroup> {
  private static final Logger log = LoggerFactory.getLogger(GroupReindexRunnable.class);

  private final ForwardedIndexGroupHandler indexer;

  @Inject
  public GroupReindexRunnable(
      ForwardedIndexGroupHandler indexer, IndexTs indexTs, OneOffRequestContext ctx) {
    super(AbstractIndexRestApiServlet.IndexName.GROUP, indexTs, ctx);
    this.indexer = indexer;
  }

  @Override
  protected ResultSet<AccountGroup> fetchItems(ReviewDb db) throws OrmException {
    return db.accountGroups().all();
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(ReviewDb db, AccountGroup g, Timestamp sinceTs) {
    try {
      Id groupId = g.getId();
      Stream<Timestamp> groupIdAudTs =
          db.accountGroupByIdAud()
              .byGroup(g.getId())
              .toList()
              .stream()
              .map(ga -> ga.getRemovedOn())
              .filter(Objects::nonNull);
      List<AccountGroupMemberAudit> groupMembersAud =
          db.accountGroupMembersAudit().byGroup(groupId).toList();
      Stream<Timestamp> groupMemberAudAddedTs =
          groupMembersAud.stream().map(ga -> ga.getKey().getAddedOn()).filter(Objects::nonNull);
      Stream<Timestamp> groupMemberAudRemovedTs =
          groupMembersAud.stream().map(ga -> ga.getRemovedOn()).filter(Objects::nonNull);
      Optional<Timestamp> groupLastTs =
          Streams.concat(groupIdAudTs, groupMemberAudAddedTs, groupMemberAudRemovedTs)
              .max(Comparator.naturalOrder());

      if (groupLastTs.isPresent() && groupLastTs.get().after(sinceTs)) {
        log.info("Index {}/{}/{}", g.getGroupUUID(), g.getName(), groupLastTs.get());
        indexer.index(g.getGroupUUID(), Operation.INDEX, Optional.empty());
        return groupLastTs;
      }
    } catch (OrmException | IOException e) {
      log.error("Reindex failed", e);
    }
    return Optional.empty();
  }
}
