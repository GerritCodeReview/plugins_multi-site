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

package com.googlesource.gerrit.plugins.multisite.validation;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.gerrit.entities.Project;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultisiteReplicationFetchFilterTest extends LocalDiskRepositoryTestCase
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
  public void shouldReturnEmptyRefsWhenAllUpToDate() throws Exception {
    newRef("refs/heads/foo");
    newRef("refs/heads/bar");
    Set<String> refs = Set.of("refs/heads/foo", "refs/heads/bar");
    doReturn(true).when(sharedRefDatabaseMock).isUpToDate(eq(projectName), any());

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager);
    Set<String> filteredRefs = fetchFilter.filter(project, refs);

    assertThat(filteredRefs).isEmpty();
  }

  @Test
  public void shouldFilterOutOneUpToDateRef() throws Exception {
    String refUpToDate = "refs/heads/uptodate";
    String outdatedRef = "refs/heads/outdated";
    newRef(refUpToDate);
    newRef(outdatedRef);
    Set<String> refsToFetch = Set.of(refUpToDate, outdatedRef);
    SharedRefDatabaseWrapper sharedRefDatabase = new FakeSharedRefDatabaseWrapper(outdatedRef);

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabase, gitRepositoryManager);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).containsExactly(outdatedRef);
  }

  @Test
  public void shouldLoadLocalVersionAndFilterOut() throws Exception {
    String temporaryOutdated = "refs/heads/temporaryOutdated";
    newRef(temporaryOutdated);

    Set<String> refsToFetch = Set.of(temporaryOutdated);
    doReturn(false).doReturn(true).when(sharedRefDatabaseMock).isUpToDate(eq(projectName), any());

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).isEmpty();

    verify(sharedRefDatabaseMock, times(2)).isUpToDate(any(), any());
  }

  @Test
  public void shouldLoadLocalVersionAndNotFilter() throws Exception {
    String temporaryOutdated = "refs/heads/temporaryOutdated";
    newRef(temporaryOutdated);

    Set<String> refsToFetch = Set.of(temporaryOutdated);
    doReturn(false).doReturn(false).when(sharedRefDatabaseMock).isUpToDate(eq(projectName), any());

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(1);
    verify(sharedRefDatabaseMock, times(2)).isUpToDate(any(), any());
  }

  @Test
  public void shouldLoadLocalVersionAndNotFilterOutWhenMissingInTheSharedRefDb() throws Exception {
    String temporaryOutdated = "refs/heads/temporaryOutdated";
    newRef(temporaryOutdated);

    Set<String> refsToFetch = Set.of(temporaryOutdated);
    doReturn(false).doReturn(false).when(sharedRefDatabaseMock).isUpToDate(eq(projectName), any());

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(1);
    verify(sharedRefDatabaseMock, times(2)).get(eq(projectName), any(), any());
    verify(sharedRefDatabaseMock, times(2)).isUpToDate(any(), any());
  }

  @Test
  public void shouldNotFilterOutWhenRefIsMissingOnlyInTheLocalRepository() throws Exception {
    String refObjectId = "0000000000000000000000000000000000000001";
    String nonExistingLocalRef = "refs/heads/temporaryOutdated";

    Set<String> refsToFetch = Set.of(nonExistingLocalRef);
    doReturn(Optional.of(refObjectId))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), any(), any());

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(1);
    verify(sharedRefDatabaseMock).get(eq(projectName), any(), any());
  }

  @Test
  public void shouldFilterOutWhenRefIsDeletedInTheSharedRefDb() throws Exception {
    String temporaryOutdated = "refs/heads/temporaryOutdated";
    newRef(temporaryOutdated);

    Set<String> refsToFetch = Set.of(temporaryOutdated);
    doReturn(Optional.of(ObjectId.zeroId().getName()))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), any(), any());

    doReturn(false).doReturn(false).when(sharedRefDatabaseMock).isUpToDate(eq(projectName), any());

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(0);
    verify(sharedRefDatabaseMock).get(eq(projectName), any(), any());
  }

  @Test
  public void shouldFilterOutNonExistingRef() throws Exception {
    String nonExisting = "refs/heads/non-existing-ref";

    Set<String> refsToFetch = Set.of(nonExisting);

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(0);
    verify(sharedRefDatabaseMock).get(any(), any(), any());
    verify(sharedRefDatabaseMock, never()).isUpToDate(any(), any());
  }

  @Test
  public void shouldFilterOutRefMissingInTheLocalRepositoryAndDeletedInSharedRefDb()
      throws Exception {
    String nonExistingLocalRef = "refs/heads/temporaryOutdated";

    Set<String> refsToFetch = Set.of(nonExistingLocalRef);
    doReturn(Optional.of(ObjectId.zeroId().getName()))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), any(), any());

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(0);
    verify(sharedRefDatabaseMock).get(eq(projectName), any(), any());
    verify(sharedRefDatabaseMock, never()).isUpToDate(any(), any());
  }

  private void newRef(String refName) throws Exception {
    repo.branch(refName).commit().create();
  }
}
