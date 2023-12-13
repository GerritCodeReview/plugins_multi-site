// Copyright (C) 2020 The Android Open Source Project
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
import static com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF;
import static com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.GitBatchRefUpdateListener;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectVersionRefUpdateTest implements RefFixture {

  private static final int A_TEST_ACCOUNT_ID = 1;
  private static final GitReferenceUpdated.UpdatedRef A_TEST_UPDATED_REF =
      new GitReferenceUpdated.UpdatedRef(
          A_TEST_REF_NAME, ObjectId.zeroId(), ObjectId.zeroId(), ReceiveCommand.Type.UPDATE);

  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();

  @Mock GitBatchRefUpdateListener.Event refUpdatedEvent;
  @Mock SharedRefDatabaseWrapper sharedRefDb;
  @Mock GitReferenceUpdated gitReferenceUpdated;
  @Mock ProjectVersionLogger verLogger;

  @Inject private ProjectConfig.Factory projectConfigFactory;
  @Inject private InMemoryRepositoryManager repoManager;
  private TestRepository<InMemoryRepository> repo;
  private ProjectConfig project;
  private RevCommit masterCommit;

  @Before
  public void setUp() throws Exception {
    InMemoryRepository inMemoryRepo = repoManager.createRepository(A_TEST_PROJECT_NAME_KEY);
    project = projectConfigFactory.create(A_TEST_PROJECT_NAME_KEY);
    project.load(inMemoryRepo);
    repo = new TestRepository<>(inMemoryRepo);
    masterCommit = repo.branch("master").commit().create();
  }

  @Test
  public void producerShouldUpdateProjectVersionUponRefUpdatedEvent() throws IOException {
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(true);
    when(refUpdatedEvent.getProjectName()).thenReturn(A_TEST_PROJECT_NAME);
    when(refUpdatedEvent.getRefNames()).thenReturn(Set.of(A_TEST_REF_NAME));

    new ProjectVersionRefUpdateImpl(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
        .onGitBatchRefUpdate(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, atMost(1))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion = readLongObject(loader);
    assertThat(storedVersion).isGreaterThan((long) masterCommit.getCommitTime());

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, storedVersion, 0);
  }

  @Test
  public void producerShouldUpdateProjectVersionOnceUponMultipleRefUpdatedEvent() {
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(true);
    when(refUpdatedEvent.getProjectName()).thenReturn(A_TEST_PROJECT_NAME);
    when(refUpdatedEvent.getRefNames())
        .thenReturn(Set.of(A_TEST_REF_NAME, A_REF_NAME_OF_A_PATCHSET));

    new ProjectVersionRefUpdateImpl(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
        .onGitBatchRefUpdate(refUpdatedEvent);

    verify(sharedRefDb, atMost(1))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));
  }

  @Test
  public void producerShouldUpdateProjectVersionOnceUponMultipleRefWithOneMetaUpdatedEvent() {
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(true);
    when(refUpdatedEvent.getProjectName()).thenReturn(A_TEST_PROJECT_NAME);
    when(refUpdatedEvent.getRefNames())
        .thenReturn(Set.of(A_TEST_REF_NAME, MULTI_SITE_VERSIONING_REF));

    new ProjectVersionRefUpdateImpl(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
        .onGitBatchRefUpdate(refUpdatedEvent);

    verify(sharedRefDb, atMost(1))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));
  }

  @Test
  public void producerShouldUsePutInsteadOfCompareAndPutWhenExtendedGlobalRefDb()
      throws IOException {
    when(sharedRefDb.isSetOperationSupported()).thenReturn(true);
    GitBatchRefUpdateListener.Event refUpdatedEvent =
        new GitReferenceUpdated.GitBatchRefUpdateEvent(
            A_TEST_PROJECT_NAME_KEY,
            Set.of(A_TEST_UPDATED_REF),
            new AccountInfo(A_TEST_ACCOUNT_ID));

    new ProjectVersionRefUpdateImpl(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
        .onGitBatchRefUpdate(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, never())
        .compareAndPut(any(Project.NameKey.class), anyString(), anyLong(), anyLong());

    verify(sharedRefDb).put(any(Project.NameKey.class), any(String.class), any(String.class));
    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion = readLongObject(loader);
    assertThat(storedVersion).isGreaterThan((long) masterCommit.getCommitTime());

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, storedVersion, 0);
  }

  @Test
  public void producerShouldUpdateProjectVersionUponForcedPushRefUpdatedEvent() throws Exception {
    Thread.sleep(1000L);
    RevCommit masterPlusOneCommit = repo.branch("master").commit().create();

    Thread.sleep(1000L);
    repo.branch("master").update(masterCommit);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(true);
    when(refUpdatedEvent.getProjectName()).thenReturn(A_TEST_PROJECT_NAME);
    when(refUpdatedEvent.getRefNames()).thenReturn(Set.of(A_TEST_REF_NAME));

    ProjectVersionRefUpdateImpl projectVersion =
        new ProjectVersionRefUpdateImpl(repoManager, sharedRefDb, gitReferenceUpdated, verLogger);
    projectVersion.onGitBatchRefUpdate(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, atMost(1))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion = readLongObject(loader);

    Optional<Long> localStoredVersion = projectVersion.getProjectLocalVersion(A_TEST_PROJECT_NAME);
    assertThat(localStoredVersion).isEqualTo(Optional.of(storedVersion));

    assertThat(storedVersion).isGreaterThan((long) masterPlusOneCommit.getCommitTime());

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, storedVersion, 0);
  }

  @Test
  public void producerShouldCreateNewProjectVersionWhenMissingUponRefUpdatedEvent()
      throws IOException {
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.empty());

    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(true);
    when(refUpdatedEvent.getProjectName()).thenReturn(A_TEST_PROJECT_NAME);
    when(refUpdatedEvent.getRefNames()).thenReturn(Set.of(A_TEST_REF_NAME));

    new ProjectVersionRefUpdateImpl(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
        .onGitBatchRefUpdate(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, atMost(1))
        .compareAndPut(any(Project.NameKey.class), isNull(), any(ObjectId.class));

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion = readLongObject(loader);
    assertThat(storedVersion).isGreaterThan((long) masterCommit.getCommitTime());

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, storedVersion, 0);
  }

  private long readLongObject(ObjectLoader loader)
      throws LargeObjectException, UnsupportedEncodingException {
    String boutString = new String(loader.getBytes(), StandardCharsets.UTF_8.name());
    return Long.parseLong(boutString);
  }

  @Test
  public void shouldNotUpdateProjectVersionWhenProjectDoesntExist() throws IOException {
    when(refUpdatedEvent.getProjectName()).thenReturn("aNonExistentProject");
    when(refUpdatedEvent.getRefNames()).thenReturn(Set.of(A_TEST_REF_NAME));

    new ProjectVersionRefUpdateImpl(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
        .onGitBatchRefUpdate(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNull();

    verifyNoInteractions(verLogger);
  }

  @Test
  public void getRemoteProjectVersionShouldReturnCorrectValue() {
    when(sharedRefDb.get(A_TEST_PROJECT_NAME_KEY, MULTI_SITE_VERSIONING_VALUE_REF, String.class))
        .thenReturn(Optional.of("123"));

    Optional<Long> version =
        new ProjectVersionRefUpdateImpl(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
            .getProjectRemoteVersion(A_TEST_PROJECT_NAME);

    assertThat(version.isPresent()).isTrue();
    assertThat(version.get()).isEqualTo(123L);
  }
}
