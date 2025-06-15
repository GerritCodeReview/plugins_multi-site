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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.googlesource.gerrit.plugins.replication.pull.Context;
import com.googlesource.gerrit.plugins.replication.pull.FetchRefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.pull.ReplicationState;
import com.googlesource.gerrit.plugins.replication.pull.ReplicationState.RefFetchResult;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

public class FetchRefReplicatedEventHandlerTest {
  private static final String LOCAL_INSTANCE_ID = "local-instance-id";
  private static final String REMOTE_INSTANCE_ID = "remote-instance-id";
  private ChangeIndexer changeIndexerMock;
  private FetchRefReplicatedEventHandler fetchRefReplicatedEventHandler;
  private static URIish sourceUri;

  @Before
  public void setUp() throws Exception {
    changeIndexerMock = mock(ChangeIndexer.class);
    fetchRefReplicatedEventHandler =
        new FetchRefReplicatedEventHandler(changeIndexerMock, LOCAL_INSTANCE_ID);
    sourceUri = new URIish("git://aSourceNode/testProject.git");
  }

  @Test
  public void onEventShouldIndexExistingChange() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/meta";
    Change.Id changeId = Change.Id.fromRef(ref);
    try {
      Context.setLocalEvent(true);
      fetchRefReplicatedEventHandler.onEvent(
          newFetchRefReplicatedEvent(
              projectNameKey, ref, ReplicationState.RefFetchResult.SUCCEEDED, LOCAL_INSTANCE_ID));
      verify(changeIndexerMock, times(1)).index(eq(projectNameKey), eq(changeId));
    } finally {
      Context.unsetLocalEvent();
    }
  }

  @Test
  public void onEventShouldIgnoreMissingObjectWhenIndexExistingChangeMeta() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/meta";
    Change.Id changeId = Change.Id.fromRef(ref);
    try {
      Context.setLocalEvent(true);
      StorageException storageExceptionForMissingObject =
          new StorageException(
              "Test storage exception",
              new MissingObjectException(ObjectId.zeroId(), "Missing unknown"));
      doThrow(storageExceptionForMissingObject)
          .when(changeIndexerMock)
          .index(eq(projectNameKey), eq(changeId));
      fetchRefReplicatedEventHandler.onEvent(
          newFetchRefReplicatedEvent(
              projectNameKey, ref, ReplicationState.RefFetchResult.SUCCEEDED, LOCAL_INSTANCE_ID));
      verify(changeIndexerMock, times(1)).index(eq(projectNameKey), eq(changeId));
    } finally {
      Context.unsetLocalEvent();
    }
  }

  @Test()
  public void onEventShouldThrowStorageExceptionWhenIndexPatchSetsWithMissingRefs() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/1";
    Change.Id changeId = Change.Id.fromRef(ref);
    try {
      Context.setLocalEvent(true);
      StorageException storageExceptionForMissingObject =
          new StorageException(
              "Test storage exception",
              new MissingObjectException(ObjectId.zeroId(), "Missing unknown"));
      doThrow(storageExceptionForMissingObject)
          .when(changeIndexerMock)
          .index(eq(projectNameKey), eq(changeId));
      StorageException indexException =
          assertThrows(
              StorageException.class,
              () ->
                  fetchRefReplicatedEventHandler.onEvent(
                      newFetchRefReplicatedEvent(
                          projectNameKey, ref, RefFetchResult.SUCCEEDED, LOCAL_INSTANCE_ID)));
      assertThat(indexException).isEqualTo(storageExceptionForMissingObject);
      verify(changeIndexerMock, times(1)).index(eq(projectNameKey), eq(changeId));
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
            projectNameKey, ref, ReplicationState.RefFetchResult.SUCCEEDED, REMOTE_INSTANCE_ID));
    verify(changeIndexerMock, never()).index(eq(projectNameKey), eq(changeId));
  }

  @Test
  public void onEventShouldIndexOnPatchSetsRef() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/1";
    Change.Id changeId = Change.Id.fromRef(ref);
    fetchRefReplicatedEventHandler.onEvent(
        newFetchRefReplicatedEvent(
            projectNameKey, ref, ReplicationState.RefFetchResult.SUCCEEDED, LOCAL_INSTANCE_ID));
    verify(changeIndexerMock).index(eq(projectNameKey), eq(changeId));
  }

  @Test
  public void onEventShouldNotIndexMissingChange() {
    fetchRefReplicatedEventHandler.onEvent(
        newFetchRefReplicatedEvent(
            Project.nameKey("testProject"),
            "invalidRef",
            ReplicationState.RefFetchResult.SUCCEEDED,
            LOCAL_INSTANCE_ID));
    verify(changeIndexerMock, never()).index(any(), any());
  }

  @Test
  public void onEventShouldNotIndexFailingChange() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/meta";
    fetchRefReplicatedEventHandler.onEvent(
        newFetchRefReplicatedEvent(
            projectNameKey, ref, ReplicationState.RefFetchResult.FAILED, LOCAL_INSTANCE_ID));
    verify(changeIndexerMock, never()).index(any(), any());
  }

  @Test
  public void onEventShouldNotIndexNotAttemptedChange() {
    Project.NameKey projectNameKey = Project.nameKey("testProject");
    String ref = "refs/changes/41/41/meta";
    fetchRefReplicatedEventHandler.onEvent(
        newFetchRefReplicatedEvent(
            projectNameKey, ref, ReplicationState.RefFetchResult.NOT_ATTEMPTED, LOCAL_INSTANCE_ID));
    verify(changeIndexerMock, never()).index(any(), any());
  }

  private FetchRefReplicatedEvent newFetchRefReplicatedEvent(
      Project.NameKey projectNameKey, String ref, RefFetchResult fetchResult, String instanceId) {
    FetchRefReplicatedEvent event =
        new FetchRefReplicatedEvent(
            projectNameKey.get(), ref, sourceUri, fetchResult, RefUpdate.Result.FAST_FORWARD);
    event.instanceId = instanceId;
    return event;
  }
}
