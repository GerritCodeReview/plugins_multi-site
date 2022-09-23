// Copyright (C) 2022 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.validation;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gerritforge.gerrit.globalrefdb.GlobalRefDatabase;
import com.gerritforge.gerrit.globalrefdb.GlobalRefDbLockException;
import com.gerritforge.gerrit.globalrefdb.GlobalRefDbSystemError;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultisiteReplicationPushFilterTest extends LocalDiskRepositoryTestCase
    implements RefFixture {

  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();

  @Mock SharedRefDatabaseWrapper sharedRefDatabaseMock;

  @Inject private InMemoryRepositoryManager gitRepositoryManager;

  String project = A_TEST_PROJECT_NAME;
  Project.NameKey projectName = A_TEST_PROJECT_NAME_KEY;

  private TestRepository<InMemoryRepository> repo;

  @Before
  public void setupTestRepo() throws Exception {
    InMemoryRepository inMemoryRepo =
        gitRepositoryManager.createRepository(A_TEST_PROJECT_NAME_KEY);
    repo = new TestRepository<>(inMemoryRepo);
  }

  @Test
  public void shouldReturnAllRefUpdatesWhenAllUpToDate() throws Exception {
    List<RemoteRefUpdate> refUpdates =
        Arrays.asList(refUpdate("refs/heads/foo"), refUpdate("refs/heads/bar"));
    doReturn(true).when(sharedRefDatabaseMock).isUpToDate(eq(projectName), any());

    MultisiteReplicationPushFilter pushFilter =
        new MultisiteReplicationPushFilter(sharedRefDatabaseMock, gitRepositoryManager);
    List<RemoteRefUpdate> filteredRefUpdates = pushFilter.filter(project, refUpdates);

    assertThat(filteredRefUpdates).containsExactlyElementsIn(refUpdates);
  }

  @Test
  public void shouldFilterOutOneOutdatedRef() throws Exception {
    RemoteRefUpdate refUpToDate = refUpdate("refs/heads/uptodate");
    RemoteRefUpdate outdatedRef = refUpdate("refs/heads/outdated");
    List<RemoteRefUpdate> refUpdates = Arrays.asList(refUpToDate, outdatedRef);
    SharedRefDatabaseWrapper sharedRefDatabase = newSharedRefDatabase(outdatedRef.getSrcRef());

    MultisiteReplicationPushFilter pushFilter =
        new MultisiteReplicationPushFilter(sharedRefDatabase, gitRepositoryManager);
    List<RemoteRefUpdate> filteredRefUpdates = pushFilter.filter(project, refUpdates);

    assertThat(filteredRefUpdates).containsExactly(refUpToDate);
  }

  @Test
  public void shouldLoadLocalVersionAndNotFilter() throws Exception {
    String refName = "refs/heads/temporaryOutdated";
    RemoteRefUpdate temporaryOutdated = refUpdate(refName);
    ObjectId latestObjectId = repo.getRepository().exactRef(refName).getObjectId();

    List<RemoteRefUpdate> refUpdates = Collections.singletonList(temporaryOutdated);
    doReturn(false).doReturn(true).when(sharedRefDatabaseMock).isUpToDate(eq(projectName), any());

    MultisiteReplicationPushFilter pushFilter =
        new MultisiteReplicationPushFilter(sharedRefDatabaseMock, gitRepositoryManager);
    List<RemoteRefUpdate> filteredRefUpdates = pushFilter.filter(project, refUpdates);

    assertThat(filteredRefUpdates).hasSize(1);
    assertThat(filteredRefUpdates.get(0).getNewObjectId()).isEqualTo(latestObjectId);

    verify(sharedRefDatabaseMock, times(2)).isUpToDate(any(), any());
  }

  @Test
  public void shouldLoadLocalVersionAndFilter() throws Exception {
    RemoteRefUpdate temporaryOutdated = refUpdate("refs/heads/temporaryOutdated");
    repo.branch("refs/heads/temporaryOutdated").commit().create();
    List<RemoteRefUpdate> refUpdates = Collections.singletonList(temporaryOutdated);
    doReturn(false).doReturn(false).when(sharedRefDatabaseMock).isUpToDate(eq(projectName), any());

    MultisiteReplicationPushFilter pushFilter =
        new MultisiteReplicationPushFilter(sharedRefDatabaseMock, gitRepositoryManager);
    List<RemoteRefUpdate> filteredRefUpdates = pushFilter.filter(project, refUpdates);

    assertThat(filteredRefUpdates).isEmpty();
    verify(sharedRefDatabaseMock, times(2)).isUpToDate(any(), any());
  }

  @Test
  public void shouldFilterOutAllOutdatedChangesRef() throws Exception {
    RemoteRefUpdate refUpToDate = refUpdate("refs/heads/uptodate");
    RemoteRefUpdate refChangeUpToDate = refUpdate("refs/changes/25/1225/2");
    RemoteRefUpdate changeMetaRef = refUpdate("refs/changes/12/4512/meta");
    RemoteRefUpdate changeRef = refUpdate("refs/changes/12/4512/1");
    List<RemoteRefUpdate> refUpdates =
        Arrays.asList(refUpToDate, refChangeUpToDate, changeMetaRef, changeRef);
    SharedRefDatabaseWrapper sharedRefDatabase = newSharedRefDatabase(changeMetaRef.getSrcRef());

    MultisiteReplicationPushFilter pushFilter =
        new MultisiteReplicationPushFilter(sharedRefDatabase, gitRepositoryManager);
    List<RemoteRefUpdate> filteredRefUpdates = pushFilter.filter(project, refUpdates);

    assertThat(filteredRefUpdates).containsExactly(refUpToDate, refChangeUpToDate);
  }

  private SharedRefDatabaseWrapper newSharedRefDatabase(String... rejectedRefs) {
    Set<String> rejectedSet = new HashSet<>();
    rejectedSet.addAll(Arrays.asList(rejectedRefs));

    GlobalRefDatabase sharedRefDatabase =
        new GlobalRefDatabase() {

          @Override
          public boolean isUpToDate(Project.NameKey project, Ref ref)
              throws GlobalRefDbLockException {
            return !rejectedSet.contains(ref.getName());
          }

          @Override
          public boolean exists(Project.NameKey project, String refName) {
            return true;
          }

          @Override
          public boolean compareAndPut(Project.NameKey project, Ref currRef, ObjectId newRefValue)
              throws GlobalRefDbSystemError {
            return false;
          }

          @Override
          public <T> boolean compareAndPut(
              Project.NameKey project, String refName, T currValue, T newValue)
              throws GlobalRefDbSystemError {
            return false;
          }

          @Override
          public AutoCloseable lockRef(Project.NameKey project, String refName)
              throws GlobalRefDbLockException {
            return null;
          }

          @Override
          public void remove(Project.NameKey project) throws GlobalRefDbSystemError {}

          @Override
          public <T> Optional<T> get(Project.NameKey project, String refName, Class<T> clazz)
              throws GlobalRefDbSystemError {
            return Optional.empty();
          }
        };
    return new SharedRefDatabaseWrapper(
        DynamicItem.itemOf(GlobalRefDatabase.class, sharedRefDatabase),
        new DisabledSharedRefLogger());
  }

  private RemoteRefUpdate refUpdate(String refName) throws Exception {
    ObjectId srcObjId = ObjectId.fromString("0000000000000000000000000000000000000001");
    Ref srcRef = new ObjectIdRef.Unpeeled(Ref.Storage.NEW, refName, srcObjId);
    repo.branch(refName).commit().create();
    return new RemoteRefUpdate(null, srcRef, "origin", false, "origin", srcObjId);
  }
}
