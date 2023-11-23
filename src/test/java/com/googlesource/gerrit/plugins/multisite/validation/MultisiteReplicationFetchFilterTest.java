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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.gerrit.entities.Project;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
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
  @Mock Configuration config;
  @Mock Configuration.ReplicationFilter replicationFilterConfig;

  @Inject private InMemoryRepositoryManager gitRepositoryManager;

  String project = A_TEST_PROJECT_NAME;
  Project.NameKey projectName = A_TEST_PROJECT_NAME_KEY;

  private TestRepository<InMemoryRepository> repo;

  @Before
  public void setupTestRepo() throws Exception {
    InMemoryRepository inMemoryRepo =
        gitRepositoryManager.createRepository(A_TEST_PROJECT_NAME_KEY);
    repo = new TestRepository<>(inMemoryRepo);
    doReturn(replicationFilterConfig).when(config).replicationFilter();
  }

  @Test
  public void shouldReturnEmptyRefsWhenAllUpToDate() throws Exception {
    String fooRefName = "refs/heads/foo";
    ObjectId fooObjectId = newRef(fooRefName).getId();
    String barRefName = "refs/heads/bar";
    ObjectId barObjectId = newRef(barRefName).getId();
    Set<String> refs = Set.of(fooRefName, barRefName);

    doReturn(Optional.of(fooObjectId.getName()))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), eq(fooRefName), eq(String.class));
    doReturn(Optional.of(barObjectId.getName()))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), eq(barRefName), eq(String.class));

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager, config);
    Set<String> filteredRefs = fetchFilter.filter(project, refs);

    assertThat(filteredRefs).isEmpty();
  }

  @Test
  public void shouldFilterOutOneUpToDateRef() throws Exception {
    String refUpToDate = "refs/heads/uptodate";
    String outdatedRef = "refs/heads/outdated";
    ObjectId upToDateObjectId = newRef(refUpToDate).getId();
    newRef(outdatedRef).getId();
    Set<String> refsToFetch = Set.of(refUpToDate, outdatedRef);

    doReturn(Optional.of(upToDateObjectId.getName()))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), eq(refUpToDate), eq(String.class));
    doReturn(Optional.of(AN_OUTDATED_OBJECT_ID.getName()))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), eq(outdatedRef), eq(String.class));

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager, config);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).containsExactly(outdatedRef);
  }

  @Test
  public void shouldLoadLocalVersionAndFilterOut() throws Exception {
    String temporaryOutdated = "refs/heads/temporaryOutdated";
    RevCommit localRef = newRef(temporaryOutdated);

    Set<String> refsToFetch = Set.of(temporaryOutdated);
    doReturn(Optional.empty())
        .doReturn(Optional.of(localRef.getId().getName()))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), eq(temporaryOutdated), eq(String.class));

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager, config);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).isEmpty();

    verify(sharedRefDatabaseMock, times(2)).get(any(), any(), any());
  }

  @Test
  public void shouldLoadLocalVersionAndNotFilter() throws Exception {
    String temporaryOutdated = "refs/heads/temporaryOutdated";
    newRef(temporaryOutdated);

    Set<String> refsToFetch = Set.of(temporaryOutdated);
    doReturn(Optional.of(AN_OUTDATED_OBJECT_ID.getName()))
        .doReturn(Optional.of(AN_OUTDATED_OBJECT_ID.getName()))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), eq(temporaryOutdated), eq(String.class));

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager, config);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(1);
    verify(sharedRefDatabaseMock, times(3))
        .get(eq(projectName), eq(temporaryOutdated), eq(String.class));
  }

  @Test
  public void shouldNotFilterOutWhenMissingInTheSharedRefDb() throws Exception {
    String temporaryOutdated = "refs/heads/temporaryOutdated";
    newRef(temporaryOutdated);

    doReturn(Optional.empty())
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), eq(temporaryOutdated), eq(String.class));

    Set<String> refsToFetch = Set.of(temporaryOutdated);

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager, config);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(1);
  }

  @Test
  public void shouldNotFilterOutWhenRefsMultisiteVersionIsPresentInSharedRefDb() throws Exception {
    String refsMultisiteVersionRef = ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF;
    RevCommit multiSiteVersionRef = newRef(refsMultisiteVersionRef);

    doReturn(Optional.of(multiSiteVersionRef.getId().getName()))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), eq(refsMultisiteVersionRef), eq(String.class));

    Set<String> refsToFetch = Set.of(refsMultisiteVersionRef);

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager, config);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(1);
  }

  @Test
  public void shouldFilterOutWhenRefIsDeletedInTheSharedRefDb() throws Exception {
    String temporaryOutdated = "refs/heads/temporaryOutdated";
    newRef(temporaryOutdated);

    Set<String> refsToFetch = Set.of(temporaryOutdated);
    doReturn(Optional.of(ObjectId.zeroId().getName()))
        .when(sharedRefDatabaseMock)
        .get(eq(projectName), eq(temporaryOutdated), eq(String.class));

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager, config);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(0);
    verify(sharedRefDatabaseMock).get(eq(projectName), any(), any());
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
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager, config);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(1);
  }

  @Test
  public void shouldNotFilterOutRefThatDoesntExistLocallyOrInSharedRefDb() throws Exception {
    String nonExisting = "refs/heads/non-existing-ref";

    Set<String> refsToFetch = Set.of(nonExisting);

    MultisiteReplicationFetchFilter fetchFilter =
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager, config);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(1);
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
        new MultisiteReplicationFetchFilter(sharedRefDatabaseMock, gitRepositoryManager, config);
    Set<String> filteredRefsToFetch = fetchFilter.filter(project, refsToFetch);

    assertThat(filteredRefsToFetch).hasSize(0);
  }

  private RevCommit newRef(String refName) throws Exception {
    return repo.branch(refName).commit().create();
  }
}
