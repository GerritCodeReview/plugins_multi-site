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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import com.googlesource.gerrit.plugins.replication.RefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.ReplicationState;
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
import org.eclipse.jgit.transport.RemoteRefUpdate;
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
    when(sharedRefDb.compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class)))
        .thenReturn(true);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdate(repoManager, sharedRefDb).onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    verify(sharedRefDb, atMost(1))
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    assertThat(ref).isNotNull();

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    String storedVersion = IOUtils.toString(loader.openStream(), StandardCharsets.UTF_8.name());
    assertThat(Long.parseLong(storedVersion)).isEqualTo(masterCommit.getCommitTime());
  }

  @Test
  public void producerShouldNotUpdateProjectVersionUponSequenceRefUpdatedEvent()
      throws IOException {
    Context.setForwardedEvent(false);
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(A_TEST_PROJECT_NAME_KEY);
    when(refUpdatedEvent.getRefName()).thenReturn("refs/sequences/changes");

    new ProjectVersionRefUpdate(repoManager, sharedRefDb).onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNull();
  }

  @Test
  public void shouldNotUpdateProjectVersionWhenProjectDoesntExist() throws IOException {
    Context.setForwardedEvent(false);
    when(refUpdatedEvent.getProjectNameKey())
        .thenReturn(Project.NameKey.parse("aNonExistentProject"));
    when(refUpdatedEvent.getRefName()).thenReturn(A_TEST_REF_NAME);

    new ProjectVersionRefUpdate(repoManager, sharedRefDb).onEvent(refUpdatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNull();
  }

  @Test
  public void consumerShouldUpdateProjectVersionUponRefReplicatedEvent() throws IOException {
    Context.setForwardedEvent(true);
    RefReplicatedEvent refReplicatedEvent =
        new RefReplicatedEvent(
            A_TEST_PROJECT_NAME,
            A_TEST_REF_NAME,
            "targetNode",
            ReplicationState.RefPushResult.SUCCEEDED,
            RemoteRefUpdate.Status.OK);

    new ProjectVersionRefUpdate(repoManager, sharedRefDb).onEvent(refReplicatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNotNull();

    verify(sharedRefDb, never())
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));

    ObjectLoader loader = repo.getRepository().open(ref.getObjectId());
    String storedVersion = IOUtils.toString(loader.openStream(), StandardCharsets.UTF_8.name());
    assertThat(Long.parseLong(storedVersion))
        .isEqualTo(Integer.toUnsignedLong(masterCommit.getCommitTime()));
  }

  @Test
  public void consumerShouldNotUpdateProjectVersionUponFailedRefReplicatedEvent()
      throws IOException {
    Context.setForwardedEvent(true);
    RefReplicatedEvent refReplicatedEvent =
        new RefReplicatedEvent(
            A_TEST_PROJECT_NAME,
            A_TEST_REF_NAME,
            "targetNode",
            ReplicationState.RefPushResult.SUCCEEDED,
            RemoteRefUpdate.Status.REJECTED_OTHER_REASON);

    new ProjectVersionRefUpdate(repoManager, sharedRefDb).onEvent(refReplicatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNull();
  }

  @Test
  public void consumerShouldNotUpdateProjectVersionUponSequenceRefReplicatedEvent()
      throws IOException {
    Context.setForwardedEvent(true);
    RefReplicatedEvent refReplicatedEvent =
        new RefReplicatedEvent(
            A_TEST_PROJECT_NAME,
            "refs/sequences/groups",
            "targetNode",
            ReplicationState.RefPushResult.SUCCEEDED,
            RemoteRefUpdate.Status.OK);

    new ProjectVersionRefUpdate(repoManager, sharedRefDb).onEvent(refReplicatedEvent);

    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);
    assertThat(ref).isNull();
  }

  @Test
  public void getRemoteProjectVersionShouldReturnCorrectValue() throws IOException {
    updateLocalVersion();
    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);
    when(sharedRefDb.get(A_TEST_PROJECT_NAME_KEY, MULTI_SITE_VERSIONING_REF, ObjectId.class))
        .thenReturn(Optional.of(ref.getObjectId()));

    Optional<Long> version =
        new ProjectVersionRefUpdate(repoManager, sharedRefDb)
            .getProjectRemoteVersion(A_TEST_PROJECT_NAME);

    assertThat(version.isPresent()).isTrue();
    assertThat(version.get()).isEqualTo(masterCommit.getCommitTime());
  }

  @Test
  public void getLocalProjectVersionShouldReturnCorrectValue() throws IOException {
    updateLocalVersion();
    Ref ref = repo.getRepository().findRef(MULTI_SITE_VERSIONING_REF);

    Optional<Long> version =
        new ProjectVersionRefUpdate(repoManager, sharedRefDb)
            .getProjectLocalVersion(A_TEST_PROJECT_NAME);

    assertThat(version.isPresent()).isTrue();
    assertThat(version.get()).isEqualTo(masterCommit.getCommitTime());
  }

  private void updateLocalVersion() {
    Context.setForwardedEvent(true);
    RefReplicatedEvent refReplicatedEvent =
        new RefReplicatedEvent(
            A_TEST_PROJECT_NAME,
            A_TEST_REF_NAME,
            "targetNode",
            ReplicationState.RefPushResult.SUCCEEDED,
            RemoteRefUpdate.Status.OK);
    new ProjectVersionRefUpdate(repoManager, sharedRefDb).onEvent(refReplicatedEvent);
  }
}
