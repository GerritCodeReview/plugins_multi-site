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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
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

    new ProjectVersionRefUpdate(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
        .onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, atMost(1))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion =
        Long.parseLong(IOUtils.toString(loader.openStream(), StandardCharsets.UTF_8.name()));
    assertThat(storedVersion).isGreaterThan((long) masterCommit.getCommitTime());

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, storedVersion, 0);
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

    new ProjectVersionRefUpdate(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
        .onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, atMost(1))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion =
        Long.parseLong(IOUtils.toString(loader.openStream(), StandardCharsets.UTF_8.name()));
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

    new ProjectVersionRefUpdate(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
        .onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, atMost(1))
        .compareAndPut(any(Project.NameKey.class), isNull(), any(ObjectId.class));

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    long storedVersion =
        Long.parseLong(IOUtils.toString(loader.openStream(), StandardCharsets.UTF_8.name()));
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

  private void producerShouldNotUpdateProjectVersionUponMagicRefUpdatedEvent(String magicRefPrefix)
      throws Exception {
    String magicRefName = magicRefPrefix + "/foo";
    Context.setForwardedEvent(false);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(magicRefName);
    repo.branch(magicRefName).commit().create();

    new ProjectVersionRefUpdate(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
        .onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNull();

    verifyZeroInteractions(verLogger);
  }

  @Test
  public void shouldNotUpdateProjectVersionWhenProjectDoesntExist() throws IOException {
    Context.setForwardedEvent(false);
    when(refUpdatedEvent.getProjectNameKey())
        .thenReturn(new Project.NameKey("aNonExistentProject"));
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdate(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
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
        new ProjectVersionRefUpdate(repoManager, sharedRefDb, gitReferenceUpdated, verLogger)
            .getProjectRemoteVersion(A_TEST_PROJECT_NAME);

    assertThat(version.isPresent()).isTrue();
    assertThat(version.get()).isEqualTo(123L);
  }
}
