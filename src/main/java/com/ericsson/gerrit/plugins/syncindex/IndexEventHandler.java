// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.syncindex;

import com.google.common.base.Objects;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.extensions.events.ChangeIndexedListener;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

class IndexEventHandler implements ChangeIndexedListener {
  private final Executor executor;
  private final RestSession restClient;
  private final String pluginName;
  private final Set<SyncIndexTask> queuedTasks = Collections
      .newSetFromMap(new ConcurrentHashMap<SyncIndexTask, Boolean>());

  @Inject
  IndexEventHandler(@SyncIndexExecutor Executor executor,
      @PluginName String pluginName,
      RestSession restClient) {
    this.restClient = restClient;
    this.executor = executor;
    this.pluginName = pluginName;
  }

  @Override
  public void onChangeIndexed(ChangeData cd) {
    executeIndexTask(cd.getId(), false);
  }

  @Override
  public void onChangeDeleted(Change.Id id) {
    executeIndexTask(id, true);
  }

  private void executeIndexTask(Change.Id id, boolean deleted) {
    if (!Context.isForwardedEvent()) {
      SyncIndexTask syncIndexTask = new SyncIndexTask(id.get(), deleted);
      if (queuedTasks.add(syncIndexTask)) {
        executor.execute(syncIndexTask);
      }
    }
  }

  class SyncIndexTask implements Runnable {
    private int changeId;
    private boolean deleted;

    SyncIndexTask(int changeId, boolean deleted) {
      this.changeId = changeId;
      this.deleted = deleted;
    }

    @Override
    public void run() {
      queuedTasks.remove(this);
      if (deleted) {
        restClient.deleteFromIndex(changeId);
      } else {
        restClient.index(changeId);
      }
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(changeId, deleted);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof SyncIndexTask)) {
        return false;
      }
      SyncIndexTask other = (SyncIndexTask) obj;
      return changeId == other.changeId && deleted == other.deleted;
    }

    @Override
    public String toString() {
      return String.format("[%s] Index change %s in target instance",
          pluginName, changeId);
    }
  }
}
