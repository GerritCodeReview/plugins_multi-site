// Copyright (C) 2015 The Android Open Source Project
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
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.common.base.Objects;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class IndexEventHandler
    implements ChangeIndexedListener,
        AccountIndexedListener,
        GroupIndexedListener,
        ProjectIndexedListener {
  private static final Logger log = LoggerFactory.getLogger(IndexEventHandler.class);
  private final Executor executor;
  private final Forwarder forwarder;
  private final String pluginName;
  private final Set<IndexTask> queuedTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final ChangeCheckerImpl.Factory changeChecker;

  @Inject
  IndexEventHandler(
      @IndexExecutor Executor executor,
      @PluginName String pluginName,
      Forwarder forwarder,
      ChangeCheckerImpl.Factory changeChecker) {
    this.forwarder = forwarder;
    this.executor = executor;
    this.pluginName = pluginName;
    this.changeChecker = changeChecker;
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
  public void onChangeIndexed(String projectName, int id) {
    executeIndexChangeTask(projectName, id, false);
  }

  @Override
  public void onChangeDeleted(int id) {
    executeIndexChangeTask("", id, true);
  }

  @Override
  public void onGroupIndexed(String groupUUID) {
    if (!Context.isForwardedEvent()) {
      IndexGroupTask task = new IndexGroupTask(groupUUID);
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  @Override
  public void onProjectIndexed(String projectName) {
    if (!Context.isForwardedEvent()) {
      IndexProjectTask task = new IndexProjectTask(projectName);
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  private void executeIndexChangeTask(String projectName, int id, boolean deleted) {
    if (!Context.isForwardedEvent()) {
      ChangeChecker checker = changeChecker.create(projectName + "~" + id);
      try {
        checker
            .newIndexEvent()
            .map(event -> new IndexChangeTask(projectName, id, deleted, event))
            .ifPresent(
                task -> {
                  if (queuedTasks.add(task)) {
                    executor.execute(task);
                  }
                });
      } catch (Exception e) {
        log.warn("Unable to create task to handle change {}~{}", projectName, id, e);
      }
    }
  }

  abstract class IndexTask implements Runnable {
    protected final IndexEvent indexEvent;

    IndexTask() {
      indexEvent = new IndexEvent();
    }

    IndexTask(IndexEvent indexEvent) {
      this.indexEvent = indexEvent;
    }

    @Override
    public void run() {
      queuedTasks.remove(this);
      execute();
    }

    abstract void execute();
  }

  class IndexChangeTask extends IndexTask {
    private final boolean deleted;
    private final int changeId;
    private final String projectName;

    IndexChangeTask(String projectName, int changeId, boolean deleted, IndexEvent indexEvent) {
      super(indexEvent);
      this.projectName = projectName;
      this.changeId = changeId;
      this.deleted = deleted;
    }

    @Override
    public void execute() {
      if (deleted) {
        forwarder.deleteChangeFromIndex(changeId, indexEvent);
      } else {
        forwarder.indexChange(projectName, changeId, indexEvent);
      }
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(IndexChangeTask.class, changeId, deleted);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexChangeTask)) {
        return false;
      }
      IndexChangeTask other = (IndexChangeTask) obj;
      return changeId == other.changeId && deleted == other.deleted;
    }

    @Override
    public String toString() {
      return String.format("[%s] Index change %s in target instance", pluginName, changeId);
    }
  }

  class IndexAccountTask extends IndexTask {
    private final int accountId;

    IndexAccountTask(int accountId) {
      this.accountId = accountId;
    }

    @Override
    public void execute() {
      forwarder.indexAccount(accountId, indexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(accountId);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexAccountTask)) {
        return false;
      }
      IndexAccountTask other = (IndexAccountTask) obj;
      return accountId == other.accountId;
    }

    @Override
    public String toString() {
      return String.format("[%s] Index account %s in target instance", pluginName, accountId);
    }
  }

  class IndexGroupTask extends IndexTask {
    private final String groupUUID;

    IndexGroupTask(String groupUUID) {
      this.groupUUID = groupUUID;
    }

    @Override
    public void execute() {
      forwarder.indexGroup(groupUUID, indexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(IndexGroupTask.class, groupUUID);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexGroupTask)) {
        return false;
      }
      IndexGroupTask other = (IndexGroupTask) obj;
      return groupUUID.equals(other.groupUUID);
    }

    @Override
    public String toString() {
      return String.format("[%s] Index group %s in target instance", pluginName, groupUUID);
    }
  }

  class IndexProjectTask extends IndexTask {
    private final String projectName;

    IndexProjectTask(String projectName) {
      this.projectName = projectName;
    }

    @Override
    public void execute() {
      forwarder.indexProject(projectName, indexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(IndexProjectTask.class, projectName);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof IndexProjectTask)) {
        return false;
      }
      IndexProjectTask other = (IndexProjectTask) obj;
      return projectName.equals(other.projectName);
    }

    @Override
    public String toString() {
      return String.format("[%s] Index project %s in target instance", pluginName, projectName);
    }
  }
}
