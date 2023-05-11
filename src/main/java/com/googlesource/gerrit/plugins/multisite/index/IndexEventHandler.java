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

import com.gerritforge.gerrit.globalrefdb.validation.ProjectsFilter;
import com.google.common.base.Objects;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwarderTask;
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
  private final Set<IndexTask> queuedTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final ChangeCheckerImpl.Factory changeChecker;
  private final ProjectsFilter projectsFilter;
  private final GroupChecker groupChecker;
  private final String instanceId;

  @Inject
  IndexEventHandler(
      @IndexExecutor Executor executor,
      DynamicSet<IndexEventForwarder> forwarders,
      ChangeCheckerImpl.Factory changeChecker,
      ProjectsFilter projectsFilter,
      GroupChecker groupChecker,
      @GerritInstanceId String instanceId) {
    this.forwarders = forwarders;
    this.executor = executor;
    this.changeChecker = changeChecker;
    this.projectsFilter = projectsFilter;
    this.groupChecker = groupChecker;
    this.instanceId = instanceId;
  }

  @Override
  public void onAccountIndexed(int id) {
    if (!Context.isForwardedEvent()) {
      IndexAccountTask task = new IndexAccountTask(new AccountIndexEvent(id, instanceId));
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
      IndexGroupTask task =
          new IndexGroupTask(
              new GroupIndexEvent(groupUUID, groupChecker.getGroupHead(groupUUID), instanceId));
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  @Override
  public void onProjectIndexed(String projectName) {
    if (!Context.isForwardedEvent() && projectsFilter.matches(projectName)) {
      IndexProjectTask task = new IndexProjectTask(new ProjectIndexEvent(projectName, instanceId));
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  private void executeIndexChangeTask(String projectName, int id) {
    if (!Context.isForwardedEvent()
        && !Context.isPullReplicationApplyObjectIndexing()
        && projectsFilter.matches(projectName)) {
      ChangeChecker checker = changeChecker.create(projectName + "~" + id);

      try {
        checker
            .newIndexEvent(projectName, id, false)
            .map(
                event -> {
                  if (Thread.currentThread().getName().contains("Batch")) {
                    return new BatchIndexChangeTask(event);
                  }
                  return new IndexChangeTask(event);
                })
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
      IndexChangeTask task = new IndexChangeTask(new ChangeIndexEvent("", id, true, instanceId));
      if (queuedTasks.add(task)) {
        executor.execute(task);
      }
    }
  }

  abstract class IndexTask extends ForwarderTask {

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
      forwarders.forEach(f -> f.index(this, changeIndexEvent));
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
      return String.format("Index change %s in target instance", changeIndexEvent.changeId);
    }
  }

  class BatchIndexChangeTask extends IndexTask {
    private final ChangeIndexEvent changeIndexEvent;

    BatchIndexChangeTask(ChangeIndexEvent changeIndexEvent) {
      this.changeIndexEvent = changeIndexEvent;
    }

    @Override
    public void execute() {
      forwarders.forEach(f -> f.batchIndex(this, changeIndexEvent));
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
      return String.format("Index change %s in target instance", changeIndexEvent.changeId);
    }
  }

  class IndexAccountTask extends IndexTask {
    private final AccountIndexEvent accountIndexEvent;

    IndexAccountTask(AccountIndexEvent accountIndexEvent) {
      this.accountIndexEvent = accountIndexEvent;
    }

    @Override
    public void execute() {
      forwarders.forEach(f -> f.index(this, accountIndexEvent));
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
      return String.format("Index account %s in target instance", accountIndexEvent.accountId);
    }
  }

  class IndexGroupTask extends IndexTask {
    private final GroupIndexEvent groupIndexEvent;

    IndexGroupTask(GroupIndexEvent groupIndexEvent) {
      this.groupIndexEvent = groupIndexEvent;
    }

    @Override
    public void execute() {
      forwarders.forEach(f -> f.index(this, groupIndexEvent));
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
      return String.format("Index group %s in target instance", groupIndexEvent.groupUUID);
    }
  }

  class IndexProjectTask extends IndexTask {
    private final ProjectIndexEvent projectIndexEvent;

    IndexProjectTask(ProjectIndexEvent projectIndexEvent) {
      this.projectIndexEvent = projectIndexEvent;
    }

    @Override
    public void execute() {
      forwarders.forEach(f -> f.index(this, projectIndexEvent));
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
      return String.format("Index project %s in target instance", projectIndexEvent.projectName);
    }
  }
}
