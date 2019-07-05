// Copyright (C) 2019 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import com.googlesource.gerrit.plugins.multisite.validation.MultisiteBeforeReplicationPushFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultisiteBeforeReplicationPushFilterTest {

  @Mock SharedRefDatabase sharedRefDatabaseMock;

  String project = "fooProject";

  @Test
  public void shouldReturnAllRefUpdatesWhenAllUpToDate() throws Exception {
    List<RemoteRefUpdate> refUpdates =
        Arrays.asList(refUpdate("refs/heads/foo"), refUpdate("refs/heads/bar"));
    doReturn(true).when(sharedRefDatabaseMock).isUpToDate(eq(project), any());

    MultisiteBeforeReplicationPushFilter pushFilter =
        new MultisiteBeforeReplicationPushFilter(sharedRefDatabaseMock);
    List<RemoteRefUpdate> filteredRefUpdates = pushFilter.filter(project, refUpdates);

    assertThat(filteredRefUpdates).containsExactlyElementsIn(refUpdates);
  }

  @Test
  public void shouldFilterOutOneOutdatedRef() throws Exception {
    RemoteRefUpdate refUpToDate = refUpdate("refs/heads/uptodate");
    RemoteRefUpdate outdatedRef = refUpdate("refs/heads/outdated");
    List<RemoteRefUpdate> refUpdates = Arrays.asList(refUpToDate, outdatedRef);
    SharedRefDatabase sharedRefDatabase = newSharedRefDatabase(outdatedRef.getSrcRef());

    MultisiteBeforeReplicationPushFilter pushFilter =
        new MultisiteBeforeReplicationPushFilter(sharedRefDatabase);
    List<RemoteRefUpdate> filteredRefUpdates = pushFilter.filter(project, refUpdates);

    assertThat(filteredRefUpdates).containsExactly(refUpToDate);
  }

  @Test
  public void shouldFilterOutAllOutdatedChangesRef() throws Exception {
    RemoteRefUpdate refUpToDate = refUpdate("refs/heads/uptodate");
    RemoteRefUpdate refChangeUpToDate = refUpdate("refs/changes/25/1225/2");
    RemoteRefUpdate changeMetaRef = refUpdate("refs/changes/12/4512/meta");
    RemoteRefUpdate changeRef = refUpdate("refs/changes/12/4512/1");
    List<RemoteRefUpdate> refUpdates =
        Arrays.asList(refUpToDate, refChangeUpToDate, changeMetaRef, changeRef);
    SharedRefDatabase sharedRefDatabase = newSharedRefDatabase(changeMetaRef.getSrcRef());

    MultisiteBeforeReplicationPushFilter pushFilter =
        new MultisiteBeforeReplicationPushFilter(sharedRefDatabase);
    List<RemoteRefUpdate> filteredRefUpdates = pushFilter.filter(project, refUpdates);

    assertThat(filteredRefUpdates).containsExactly(refUpToDate, refChangeUpToDate);
  }

  private SharedRefDatabase newSharedRefDatabase(String... rejectedRefs) {
    Set<String> rejectedSet = new HashSet<>();
    rejectedSet.addAll(Arrays.asList(rejectedRefs));

    SharedRefDatabase sharedRefDatabase =
        new SharedRefDatabase() {

          @Override
          public void removeProject(String project) throws IOException {}

          @Override
          public AutoCloseable lockRef(String project, String refName) throws SharedLockException {
            return null;
          }

          @Override
          public boolean isUpToDate(String project, Ref ref) throws SharedLockException {
            return !rejectedSet.contains(ref.getName());
          }

          @Override
          public boolean exists(String project, String refName) {
            return true;
          }

          @Override
          public boolean compareAndPut(String project, Ref currRef, ObjectId newRefValue)
              throws IOException {
            return false;
          }
        };
    return sharedRefDatabase;
  }

  private RemoteRefUpdate refUpdate(String refName) throws IOException {
    ObjectId srcObjId = ObjectId.fromString("0000000000000000000000000000000000000001");
    Ref srcRef = new ObjectIdRef.Unpeeled(Ref.Storage.NEW, refName, srcObjId);
    return new RemoteRefUpdate(null, srcRef, "origin", false, "origin", srcObjId);
  }
}
