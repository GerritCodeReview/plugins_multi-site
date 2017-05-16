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

package com.ericsson.gerrit.plugins.highavailability.index;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.common.base.Objects;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

class IndexEventHandler implements ChangeIndexedListener, AccountIndexedListener {
  private final Executor executor;
  private final Forwarder forwarder;
  private final String pluginName;
  private final Set<IndexTask> queuedTasks =
      Collections.newSetFromMap(new ConcurrentHashMap<IndexTask, Boolean>());

  @Inject
  IndexEventHandler(
      @IndexExecutor Executor executor, @PluginName String pluginName, Forwarder forwarder) {
    this.forwarder = forwarder;
    this.executor = executor;
    this.pluginName = pluginName;
  }

  @Override
  public void onAccountIndexed(int id) {
    if (!Context.isForwardedEvent()) {
      IndexAccountTask task = new IndexAccountTask(id);
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  @Override
  public void onChangeIndexed(int id) {
    executeIndexChangeTask(id, false);
  }

  @Override
  public void onChangeDeleted(int id) {
    executeIndexChangeTask(id, true);
  }

  private void executeIndexChangeTask(int id, boolean deleted) {
    if (!Context.isForwardedEvent()) {
      IndexChangeTask task = new IndexChangeTask(id, deleted);
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  abstract class IndexTask implements Runnable {
    protected int id;

    IndexTask(int id) {
      this.id = id;
    }

    @Override
    public void run() {
      queuedTasks.remove(this);
      execute();
    }

    abstract void execute();
  }

  class IndexChangeTask extends IndexTask {
    private boolean deleted;

    IndexChangeTask(int changeId, boolean deleted) {
      super(changeId);
      this.deleted = deleted;
    }

    @Override
    public void execute() {
      if (deleted) {
        forwarder.deleteChangeFromIndex(id);
      } else {
        forwarder.indexChange(id);
      }
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(IndexChangeTask.class, id, deleted);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexChangeTask)) {
        return false;
      }
      IndexChangeTask other = (IndexChangeTask) obj;
      return id == other.id && deleted == other.deleted;
    }

    @Override
    public String toString() {
      return String.format("[%s] Index change %s in target instance", pluginName, id);
    }
  }

  class IndexAccountTask extends IndexTask {

    IndexAccountTask(int accountId) {
      super(accountId);
    }

    @Override
    public void execute() {
      forwarder.indexAccount(id);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(IndexAccountTask.class, id);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexAccountTask)) {
        return false;
      }
      IndexAccountTask other = (IndexAccountTask) obj;
      return id == other.id;
    }

    @Override
    public String toString() {
      return String.format("[%s] Index account %s in target instance", pluginName, id);
    }
  }
}
