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
import static com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdateImpl.DEFAULT_NUMBER_OF_RETRIES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.globalrefdb.GlobalRefDbSystemError;
import com.gerritforge.gerrit.globalrefdb.validation.ProjectsFilter;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectVersionRefUpdateTest implements RefFixture {

  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();

  @Mock RefUpdatedEvent refUpdatedEvent;
  @Mock SharedRefDatabaseWrapper sharedRefDb;
  @Mock GitReferenceUpdated gitReferenceUpdated;
  @Mock ProjectVersionLogger verLogger;
  @Mock ProjectsFilter projectsFilter;

  @Inject private ProjectConfig.Factory projectConfigFactory;
  @Inject private InMemoryRepositoryManager repoManager;

  private TestRepository<InMemoryRepository> repo;
  private ProjectConfig project;
  private RevCommit masterCommit;

  @Before
  public void setUp() throws Exception {
    when(projectsFilter.matches(any(Event.class))).thenReturn(true);
    InMemoryRepository inMemoryRepo = repoManager.createRepository(A_TEST_PROJECT_NAME_KEY);
    project = projectConfigFactory.create(A_TEST_PROJECT_NAME_KEY);
    project.load(inMemoryRepo);
    repo = new TestRepository<>(inMemoryRepo);
    masterCommit = repo.branch("master").commit().create();
  }

  @After
  public void tearDown() {
    Context.unsetForwardedEvent();
  }

  @Test
  public void producerShouldUpdateProjectVersionUponRefUpdatedEvent() throws IOException {
    Context.setForwardedEvent(false);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.of("26f7ee61bf0e470e8393c884526eec8a9b943a63"));
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(true);
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(true);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

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
  public void producerShouldRetryUpdateProjectVersionUponGlobalRefDbFailure() throws IOException {
    Context.setForwardedEvent(false);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.of("26f7ee61bf0e470e8393c884526eec8a9b943a63"));
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(false)
        .thenReturn(true);
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(true);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, times(2))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));
    verify(sharedRefDb).compareAndPut(any(Project.NameKey.class), any(String.class), any(), any());

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion = readLongObject(loader);
    assertThat(storedVersion).isGreaterThan((long) masterCommit.getCommitTime());

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, storedVersion, 0);
  }

  @Test
  public void producerShouldPropagateExceptionFromProjectVersionUpdateRetry() throws IOException {
    Context.setForwardedEvent(false);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.of("26f7ee61bf0e470e8393c884526eec8a9b943a63"));
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(false)
        .thenThrow(new GlobalRefDbSystemError("Error from global refdb", new Exception()));
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, times(2))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    verify(sharedRefDb, never())
        .compareAndPut(any(Project.NameKey.class), any(String.class), any(), any());

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion = readLongObject(loader);
    assertThat(storedVersion).isGreaterThan((long) masterCommit.getCommitTime());

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, storedVersion, 0);
  }

  @Test
  public void producerShouldSkipRetryWhenGlobalRefDbErrorExceptionFromFirstAttempt()
      throws IOException {
    Context.setForwardedEvent(false);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.of("26f7ee61bf0e470e8393c884526eec8a9b943a63"));
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenThrow(new GlobalRefDbSystemError("Error from global refdb", new Exception()));
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    verify(sharedRefDb)
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    verify(sharedRefDb, never())
        .compareAndPut(any(Project.NameKey.class), any(String.class), any(), any());
  }

  @Test
  public void producerShouldRetryUpdateProjectVersionValueUponGlobalRefDbFailure()
      throws IOException {
    Context.setForwardedEvent(false);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.of("26f7ee61bf0e470e8393c884526eec8a9b943a63"));
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(true);
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(false)
        .thenReturn(true);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb)
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    verify(sharedRefDb, times(2))
        .compareAndPut(any(Project.NameKey.class), any(String.class), any(), any());

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion = readLongObject(loader);
    assertThat(storedVersion).isGreaterThan((long) masterCommit.getCommitTime());

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, storedVersion, 0);
  }

  @Test
  public void producerShouldGiveUpProjectVersionUpdateUponMaxUpdateFailures() throws IOException {
    Context.setForwardedEvent(false);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.of("26f7ee61bf0e470e8393c884526eec8a9b943a63"));
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(false);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    verify(sharedRefDb, times(DEFAULT_NUMBER_OF_RETRIES + 1))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    verify(sharedRefDb, never())
        .compareAndPut(any(Project.NameKey.class), any(String.class), any(), any());
  }

  @Test
  public void producerShouldGiveUpProjectVersionUpdateWhenNewerValueInGlobalRefDb()
      throws IOException {
    Context.setForwardedEvent(false);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.of("26f7ee61bf0e470e8393c884526eec8a9b943a63"));
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)))
        // Return newer value from sharedRefDb
        .thenReturn(Optional.of("" + System.currentTimeMillis() * 2));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(false)
        .thenReturn(false)
        .thenReturn(true);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    verify(sharedRefDb, times(2))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    verify(sharedRefDb, never())
        .compareAndPut(any(Project.NameKey.class), any(String.class), any(), any());
  }

  @Test
  public void producerShouldGiveUpProjectVersionValueUpdateWhenNewerValueInGlobalRefDb()
      throws IOException {
    Context.setForwardedEvent(false);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.of("26f7ee61bf0e470e8393c884526eec8a9b943a63"));
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)))
        // Return newer value from sharedRefDb
        .thenReturn(Optional.of("" + System.currentTimeMillis() * 2));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(true);
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(false)
        .thenReturn(false)
        .thenReturn(true);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    verify(sharedRefDb)
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    verify(sharedRefDb, times(2))
        .compareAndPut(any(Project.NameKey.class), any(String.class), any(), any());
  }

  @Test
  public void producerShouldGiveUpProjectVersionValueUpdateUponMaxUpdateFailures()
      throws IOException {
    Context.setForwardedEvent(false);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.of("26f7ee61bf0e470e8393c884526eec8a9b943a63"));
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(true);
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(false);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    verify(sharedRefDb)
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    verify(sharedRefDb, times(DEFAULT_NUMBER_OF_RETRIES + 1))
        .compareAndPut(any(Project.NameKey.class), any(String.class), any(), any());
  }

  @Test
  public void producerShouldUpdateProjectVersionUponForcedPushRefUpdatedEvent() throws Exception {
    Context.setForwardedEvent(false);

    Thread.sleep(1000L);
    RevCommit masterPlusOneCommit = repo.branch("master").commit().create();

    Thread.sleep(1000L);
    repo.branch("master").update(masterCommit);

    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.of("26f7ee61bf0e470e8393c884526eec8a9b943a63"));
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.of("" + (masterCommit.getCommitTime() - 1)));
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(true);
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(true);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    ProjectVersionRefUpdateImpl projectVersion =
        new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter);
    projectVersion.onEvent(refUpdatedEvent);

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
    Context.setForwardedEvent(false);
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF,
            String.class))
        .thenReturn(Optional.empty());
    when(sharedRefDb.get(
            A_TEST_PROJECT_NAME_KEY,
            ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_VALUE_REF,
            String.class))
        .thenReturn(Optional.empty());

    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(true);
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(String.class), any(), any()))
        .thenReturn(true);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, atMost(1))
        .compareAndPut(any(Project.NameKey.class), isNull(), any(ObjectId.class));

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion = readLongObject(loader);
    assertThat(storedVersion).isGreaterThan((long) masterCommit.getCommitTime());

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, storedVersion, 0);
  }

  @Test
  public void producerShouldNotUpdateProjectVersionUponSequenceRefUpdatedEvent() throws Exception {
    producerShouldNotUpdateProjectVersionUponMagicRefUpdatedEvent(RefNames.REFS_SEQUENCES);
  }

  @Test
  public void producerShouldNotUpdateProjectVersionUponStarredChangesRefUpdatedEvent()
      throws Exception {
    producerShouldNotUpdateProjectVersionUponMagicRefUpdatedEvent(RefNames.REFS_STARRED_CHANGES);
  }

  private long readLongObject(ObjectLoader loader)
      throws LargeObjectException, UnsupportedEncodingException {
    String boutString = new String(loader.getBytes(), StandardCharsets.UTF_8.name());
    return Long.parseLong(boutString);
  }

  private void producerShouldNotUpdateProjectVersionUponMagicRefUpdatedEvent(String magicRefPrefix)
      throws Exception {
    String magicRefName = magicRefPrefix + "/foo";
    Context.setForwardedEvent(false);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(magicRefName);
    repo.branch(magicRefName).commit().create();

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNull();

    verifyZeroInteractions(verLogger);
  }

  @Test
  public void shouldNotUpdateProjectVersionWhenProjectDoesntExist() throws IOException {
    Context.setForwardedEvent(false);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(Project.nameKey("aNonExistentProject"));
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdateImpl(
            repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
        .onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNull();

    verifyZeroInteractions(verLogger);
  }

  @Test
  public void getRemoteProjectVersionShouldReturnCorrectValue() {
    when(sharedRefDb.get(A_TEST_PROJECT_NAME_KEY, MULTI_SITE_VERSIONING_VALUE_REF, String.class))
        .thenReturn(Optional.of("123"));

    Optional<Long> version =
        new ProjectVersionRefUpdateImpl(
                repoManager, sharedRefDb, gitReferenceUpdated, verLogger, projectsFilter)
            .getProjectRemoteVersion(A_TEST_PROJECT_NAME);

    assertThat(version.isPresent()).isTrue();
    assertThat(version.get()).isEqualTo(123L);
  }
}
