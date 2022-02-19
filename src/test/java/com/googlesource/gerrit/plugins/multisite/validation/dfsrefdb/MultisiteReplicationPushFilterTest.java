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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.validation.DisabledSharedRefLogger;
import com.googlesource.gerrit.plugins.multisite.validation.MultisiteReplicationPushFilter;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

  String project;

  private TestRepository<InMemoryRepository> repo;

  @Override
  @Before
  public void setUp() throws Exception {
    setUp(A_TEST_PROJECT_NAME, A_TEST_PROJECT_NAME_KEY);
  }

  private void setUp(String projectName, Project.NameKey projectNameKey) throws Exception {
    project = projectName;
    InMemoryRepository inMemoryRepo = gitRepositoryManager.createRepository(projectNameKey);
    repo = new TestRepository<>(inMemoryRepo);
  }

  @Test
  public void shouldReturnAllRefUpdatesWhenAllUpToDate() throws Exception {
    List<RemoteRefUpdate> refUpdates =
        Arrays.asList(refUpdate("refs/heads/foo"), refUpdate("refs/heads/bar"));
    doReturn(true).when(sharedRefDatabaseMock).isUpToDate(eq(project), any());

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
    RemoteRefUpdate temporaryOutdated = refUpdate("refs/heads/temporaryOutdated");
    List<RemoteRefUpdate> refUpdates = Collections.singletonList(temporaryOutdated);
    doReturn(false).doReturn(true).when(sharedRefDatabaseMock).isUpToDate(eq(project), any());

    MultisiteReplicationPushFilter pushFilter =
        new MultisiteReplicationPushFilter(sharedRefDatabaseMock, gitRepositoryManager);
    List<RemoteRefUpdate> filteredRefUpdates = pushFilter.filter(project, refUpdates);

    assertThat(filteredRefUpdates).containsExactly(temporaryOutdated);
    verify(sharedRefDatabaseMock, times(2)).isUpToDate(any(), any());
  }

  @Test
  public void shouldLoadLocalVersionAndFilter() throws Exception {
    RemoteRefUpdate temporaryOutdated = refUpdate("refs/heads/temporaryOutdated");
    repo.branch("refs/heads/temporaryOutdated").commit().create();
    List<RemoteRefUpdate> refUpdates = Collections.singletonList(temporaryOutdated);
    doReturn(false).doReturn(false).when(sharedRefDatabaseMock).isUpToDate(eq(project), any());

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

  @Test
  public void shouldFilterProjectNameEndingWithPlus() throws Exception {
    setUp("testrepo+", new Project.NameKey("testrepo+"));
    RemoteRefUpdate refUpdate = refUpdate("refs/heads/foo");
    doReturn(true).when(sharedRefDatabaseMock).isUpToDate(eq(project), any());

    MultisiteReplicationPushFilter pushFilter =
        new MultisiteReplicationPushFilter(sharedRefDatabaseMock, gitRepositoryManager);
    List<RemoteRefUpdate> filteredRefUpdates = pushFilter.filter(project, Arrays.asList(refUpdate));

    assertThat(filteredRefUpdates).contains(refUpdate);
  }

  private SharedRefDatabaseWrapper newSharedRefDatabase(String... rejectedRefs) {
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
    return new SharedRefDatabaseWrapper(
        DynamicItem.itemOf(SharedRefDatabase.class, sharedRefDatabase),
        new DisabledSharedRefLogger());
  }

  private RemoteRefUpdate refUpdate(String refName) throws Exception {
    ObjectId srcObjId = ObjectId.fromString("0000000000000000000000000000000000000001");
    Ref srcRef = new ObjectIdRef.Unpeeled(Ref.Storage.NEW, refName, srcObjId);
    repo.branch(refName).commit().create();
    return new RemoteRefUpdate(null, srcRef, "origin", false, "origin", srcObjId);
  }
}
