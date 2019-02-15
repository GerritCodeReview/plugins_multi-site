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

package com.googlesource.gerrit.plugins.multisite.index;

import com.google.common.base.Objects;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
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
  private final DynamicSet<IndexEventForwarder> forwarders;
  private final String pluginName;
  private final Set<IndexTask> queuedTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final ChangeCheckerImpl.Factory changeChecker;

  @Inject
  IndexEventHandler(
      @IndexExecutor Executor executor,
      @PluginName String pluginName,
      DynamicSet<IndexEventForwarder> forwarders,
      ChangeCheckerImpl.Factory changeChecker) {
    this.forwarders = forwarders;
    this.executor = executor;
    this.pluginName = pluginName;
    this.changeChecker = changeChecker;
  }

  @Override
  public void onAccountIndexed(int id) {
    if (!Context.isForwardedEvent()) {
      IndexAccountTask task = new IndexAccountTask(new AccountIndexEvent(id));
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  @Override
  public void onChangeIndexed(String projectName, int id) {
    executeIndexChangeTask(projectName, id);
  }

  @Override
  public void onChangeDeleted(int id) {
    executeDeleteChangeTask(id);
  }

  @Override
  public void onGroupIndexed(String groupUUID) {
    if (!Context.isForwardedEvent()) {
      IndexGroupTask task = new IndexGroupTask(new GroupIndexEvent(groupUUID));
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  @Override
  public void onProjectIndexed(String projectName) {
    if (!Context.isForwardedEvent()) {
      IndexProjectTask task = new IndexProjectTask(new ProjectIndexEvent(projectName));
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  private void executeIndexChangeTask(String projectName, int id) {
    if (!Context.isForwardedEvent()) {
      ChangeChecker checker = changeChecker.create(projectName + "~" + id);
      try {
        checker
            .newIndexEvent(projectName, id, false)
            .map(event -> new IndexChangeTask(event))
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

  private void executeDeleteChangeTask(int id) {
    if (!Context.isForwardedEvent()) {
      IndexChangeTask task = new IndexChangeTask(new ChangeIndexEvent("", id, true));
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  abstract class IndexTask implements Runnable {
    @Override
    public void run() {
      queuedTasks.remove(this);
      execute();
    }

    abstract void execute();
  }

  class IndexChangeTask extends IndexTask {
    private final ChangeIndexEvent changeIndexEvent;

    IndexChangeTask(ChangeIndexEvent changeIndexEvent) {
      this.changeIndexEvent = changeIndexEvent;
    }

    @Override
    public void execute() {
      if (changeIndexEvent.deleted) {
        forwarders.forEach(f -> f.deleteChangeFromIndex(changeIndexEvent));
      } else {
        forwarders.forEach(f -> f.indexChange(changeIndexEvent));
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      IndexChangeTask that = (IndexChangeTask) o;
      return Objects.equal(changeIndexEvent, that.changeIndexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(changeIndexEvent);
    }

    @Override
    public String toString() {
      return String.format(
          "[%s] Index change %s in target instance", pluginName, changeIndexEvent.changeId);
    }
  }

  class IndexAccountTask extends IndexTask {
    private final AccountIndexEvent accountIndexEvent;

    IndexAccountTask(AccountIndexEvent accountIndexEvent) {
      this.accountIndexEvent = accountIndexEvent;
    }

    @Override
    public void execute() {
      forwarders.forEach(f -> f.indexAccount(accountIndexEvent));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      IndexAccountTask that = (IndexAccountTask) o;
      return Objects.equal(accountIndexEvent, that.accountIndexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(accountIndexEvent);
    }

    @Override
    public String toString() {
      return String.format(
          "[%s] Index account %s in target instance", pluginName, accountIndexEvent.accountId);
    }
  }

  class IndexGroupTask extends IndexTask {
    private final GroupIndexEvent groupIndexEvent;

    IndexGroupTask(GroupIndexEvent groupIndexEvent) {
      this.groupIndexEvent = groupIndexEvent;
    }

    @Override
    public void execute() {
      forwarders.forEach(f -> f.indexGroup(groupIndexEvent));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      IndexGroupTask that = (IndexGroupTask) o;
      return Objects.equal(groupIndexEvent, that.groupIndexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(groupIndexEvent);
    }

    @Override
    public String toString() {
      return String.format(
          "[%s] Index group %s in target instance", pluginName, groupIndexEvent.groupUUID);
    }
  }

  class IndexProjectTask extends IndexTask {
    private final ProjectIndexEvent projectIndexEvent;

    IndexProjectTask(ProjectIndexEvent projectIndexEvent) {
      this.projectIndexEvent = projectIndexEvent;
    }

    @Override
    public void execute() {
      forwarders.forEach(f -> f.indexProject(projectIndexEvent));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      IndexProjectTask that = (IndexProjectTask) o;
      return Objects.equal(projectIndexEvent, that.projectIndexEvent);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(projectIndexEvent);
    }

    @Override
    public String toString() {
      return String.format(
          "[%s] Index project %s in target instance", pluginName, projectIndexEvent.projectName);
    }
  }
}
