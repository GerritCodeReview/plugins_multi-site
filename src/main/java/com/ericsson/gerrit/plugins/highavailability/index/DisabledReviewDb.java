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

package com.ericsson.gerrit.plugins.highavailability.index;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.server.AccountGroupAccess;
import com.google.gerrit.reviewdb.server.AccountGroupByIdAccess;
import com.google.gerrit.reviewdb.server.AccountGroupByIdAudAccess;
import com.google.gerrit.reviewdb.server.AccountGroupMemberAccess;
import com.google.gerrit.reviewdb.server.AccountGroupMemberAuditAccess;
import com.google.gerrit.reviewdb.server.AccountGroupNameAccess;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ChangeMessageAccess;
import com.google.gerrit.reviewdb.server.PatchLineCommentAccess;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gerrit.reviewdb.server.PatchSetApprovalAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.SchemaVersionAccess;
import com.google.gerrit.reviewdb.server.SystemConfigAccess;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.StatementExecutor;
import java.util.Map;

/** ReviewDb that is disabled. */
public class DisabledReviewDb implements ReviewDb {
  public static class Disabled extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private Disabled() {
      super("ReviewDb is disabled for changes");
    }
  }

  public static class DisabledChangeAccess implements ChangeAccess {

    @Override
    public String getRelationName() {
      throw new Disabled();
    }

    @Override
    public int getRelationID() {
      throw new Disabled();
    }

    @Override
    public ResultSet<Change> iterateAllEntities() throws OrmException {
      throw new Disabled();
    }

    @Override
    public Id primaryKey(Change entity) {
      throw new Disabled();
    }

    @Override
    public Map<Id, Change> toMap(Iterable<Change> c) {
      throw new Disabled();
    }

    @Override
    public CheckedFuture<Change, OrmException> getAsync(Id key) {
      throw new Disabled();
    }

    @Override
    public ResultSet<Change> get(Iterable<Id> keys) throws OrmException {
      throw new Disabled();
    }

    @Override
    public void insert(Iterable<Change> instances) throws OrmException {
      throw new Disabled();
    }

    @Override
    public void update(Iterable<Change> instances) throws OrmException {
      throw new Disabled();
    }

    @Override
    public void upsert(Iterable<Change> instances) throws OrmException {
      throw new Disabled();
    }

    @Override
    public void deleteKeys(Iterable<Id> keys) throws OrmException {
      throw new Disabled();
    }

    @Override
    public void delete(Iterable<Change> instances) throws OrmException {
      throw new Disabled();
    }

    @Override
    public void beginTransaction(Id key) throws OrmException {
      throw new Disabled();
    }

    @Override
    public Change atomicUpdate(Id key, AtomicUpdate<Change> update) throws OrmException {
      throw new Disabled();
    }

    @Override
    public Change get(Id id) throws OrmException {
      return null;
    }

    @Override
    public ResultSet<Change> all() throws OrmException {
      return null;
    }
  }

  @Override
  public void close() {
    // Do nothing.
  }

  @Override
  public void commit() {
    throw new Disabled();
  }

  @Override
  public void rollback() {
    throw new Disabled();
  }

  @Override
  public void updateSchema(StatementExecutor e) {
    throw new Disabled();
  }

  @Override
  public void pruneSchema(StatementExecutor e) {
    throw new Disabled();
  }

  @Override
  public Access<?, ?>[] allRelations() {
    throw new Disabled();
  }

  @Override
  public SchemaVersionAccess schemaVersion() {
    throw new Disabled();
  }

  @Override
  public SystemConfigAccess systemConfig() {
    throw new Disabled();
  }

  @Override
  public AccountGroupAccess accountGroups() {
    throw new Disabled();
  }

  @Override
  public AccountGroupNameAccess accountGroupNames() {
    throw new Disabled();
  }

  @Override
  public AccountGroupMemberAccess accountGroupMembers() {
    throw new Disabled();
  }

  @Override
  public AccountGroupMemberAuditAccess accountGroupMembersAudit() {
    throw new Disabled();
  }

  @Override
  public ChangeAccess changes() {
    return new DisabledChangeAccess();
  }

  @Override
  public PatchSetApprovalAccess patchSetApprovals() {
    throw new Disabled();
  }

  @Override
  public ChangeMessageAccess changeMessages() {
    throw new Disabled();
  }

  @Override
  public PatchSetAccess patchSets() {
    throw new Disabled();
  }

  @Override
  public PatchLineCommentAccess patchComments() {
    throw new Disabled();
  }

  @Override
  public AccountGroupByIdAccess accountGroupById() {
    throw new Disabled();
  }

  @Override
  public AccountGroupByIdAudAccess accountGroupByIdAud() {
    throw new Disabled();
  }

  @Override
  public int nextAccountId() {
    throw new Disabled();
  }

  @Override
  public int nextAccountGroupId() {
    throw new Disabled();
  }

  @Override
  public int nextChangeId() {
    throw new Disabled();
  }
}
