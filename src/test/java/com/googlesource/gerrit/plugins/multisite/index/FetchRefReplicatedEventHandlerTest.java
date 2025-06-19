// Copyright (C) 2023 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.googlesource.gerrit.plugins.replication.pull.Context;
import com.googlesource.gerrit.plugins.replication.pull.FetchRefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.pull.ReplicationState;
import com.googlesource.gerrit.plugins.replication.pull.ReplicationState.RefFetchResult;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

public class FetchRefReplicatedEventHandlerTest {
  private static final String LOCAL_INSTANCE_ID = "local-instance-id";
  private static final String REMOTE_INSTANCE_ID = "remote-instance-id";
  private static final Project.NameKey PROJECT_NAME_KEY = Project.nameKey("testProject");
  private ChangeIndexer changeIndexerMock;
  private GitRepositoryManager gitRepositoryManager;
  private Repository repository;
  private FetchRefReplicatedEventHandler fetchRefReplicatedEventHandler;
  private static URIish sourceUri;

  @Before
  public void setUp() throws Exception {
    changeIndexerMock = mock(ChangeIndexer.class);
    gitRepositoryManager = mock(GitRepositoryManager.class);
    repository = mock(Repository.class);
    doReturn(repository).when(gitRepositoryManager).openRepository(any());
    fetchRefReplicatedEventHandler =
        new FetchRefReplicatedEventHandler(
            changeIndexerMock, LOCAL_INSTANCE_ID, gitRepositoryManager);
    sourceUri = new URIish("git://aSourceNode/testProject.git");
  }

  @Test
  public void onEventShouldIndexExistingChange() {
    String ref = "refs/changes/41/41/meta";
    Change.Id changeId = Change.Id.fromRef(ref);
    try {
      Context.setLocalEvent(true);
      fetchRefReplicatedEventHandler.onEvent(
          newFetchRefReplicatedEvent(
              PROJECT_NAME_KEY,
              ref,
              ReplicationState.RefFetchResult.SUCCEEDED,
              LOCAL_INSTANCE_ID,
              RefUpdate.Result.FAST_FORWARD));
      verify(changeIndexerMock, times(1)).index(eq(PROJECT_NAME_KEY), eq(changeId));
    } finally {
      Context.unsetLocalEvent();
    }
  }

  @Test
  public void onEventShouldDeleteChangeFromIndex() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/meta";
    Change.Id changeId = Change.Id.fromRef(ref);
    try {
      Context.setLocalEvent(true);
      fetchRefReplicatedEventHandler.onEvent(
          newFetchRefReplicatedEvent(
              projectNameKey,
              ref,
              ReplicationState.RefFetchResult.SUCCEEDED,
              LOCAL_INSTANCE_ID,
              RefUpdate.Result.FORCED));
      verify(changeIndexerMock, times(1)).delete(eq(changeId));
    } finally {
      Context.unsetLocalEvent();
    }
  }

  @Test
  public void onEventShouldNotIndexIfNotLocalEvent() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/meta";
    Change.Id changeId = Change.Id.fromRef(ref);
    fetchRefReplicatedEventHandler.onEvent(
        newFetchRefReplicatedEvent(
            projectNameKey,
            ref,
            ReplicationState.RefFetchResult.SUCCEEDED,
            REMOTE_INSTANCE_ID,
            RefUpdate.Result.FAST_FORWARD));
    verify(changeIndexerMock, never()).index(eq(projectNameKey), eq(changeId));
  }

  @Test
  public void onEventShouldIndexOnlyMetaRef() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/1";
    Change.Id changeId = Change.Id.fromRef(ref);
    fetchRefReplicatedEventHandler.onEvent(
        newFetchRefReplicatedEvent(
            projectNameKey,
            ref,
            ReplicationState.RefFetchResult.SUCCEEDED,
            LOCAL_INSTANCE_ID,
            RefUpdate.Result.FAST_FORWARD));
    verify(changeIndexerMock, never()).index(eq(projectNameKey), eq(changeId));
  }

  @Test
  public void onEventShouldNotIndexMissingChange() {
    fetchRefReplicatedEventHandler.onEvent(
        newFetchRefReplicatedEvent(
            Project.nameKey("testProject"),
            "invalidRef",
            ReplicationState.RefFetchResult.SUCCEEDED,
            LOCAL_INSTANCE_ID,
            RefUpdate.Result.FAST_FORWARD));
    verify(changeIndexerMock, never()).index(any(), any());
  }

  @Test
  public void onEventShouldNotIndexFailingChange() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/meta";
    fetchRefReplicatedEventHandler.onEvent(
        newFetchRefReplicatedEvent(
            projectNameKey,
            ref,
            ReplicationState.RefFetchResult.FAILED,
            LOCAL_INSTANCE_ID,
            RefUpdate.Result.FAST_FORWARD));
    verify(changeIndexerMock, never()).index(any(), any());
  }

  @Test
  public void onEventShouldNotIndexNotAttemptedChange() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/meta";
    fetchRefReplicatedEventHandler.onEvent(
        newFetchRefReplicatedEvent(
            projectNameKey,
            ref,
            ReplicationState.RefFetchResult.NOT_ATTEMPTED,
            LOCAL_INSTANCE_ID,
            RefUpdate.Result.FAST_FORWARD));
    verify(changeIndexerMock, never()).index(any(), any());
  }

  private FetchRefReplicatedEvent newFetchRefReplicatedEvent(
      Project.NameKey projectNameKey,
      String ref,
      RefFetchResult fetchResult,
      String instanceId,
      RefUpdate.Result refUpdateResult) {
    FetchRefReplicatedEvent event =
        new FetchRefReplicatedEvent(
            projectNameKey.get(), ref, sourceUri, fetchResult, refUpdateResult);
    event.instanceId = instanceId;
    return event;
  }
}
