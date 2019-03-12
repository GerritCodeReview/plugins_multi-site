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
// Copyright (C) 2018 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture.aRefObject;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.junit.Before;
import org.junit.Test;

public class ZKRefsSharedDatabaseTest extends ZookeeperTestContainerSupport {

  ZkSharedRefDatabase zkSharedRefDatabase;

  @Override
  @Before
  public void setUp() throws IOException {
    super.setUp();
    zkSharedRefDatabase =
        new ZkSharedRefDatabase(curator, Duration.ofMinutes(10), UUID.randomUUID());
  }

  @Test
  public void shouldCreateANewRef() {
    ObjectId objectId = RefFixture.anObjectId();
    String refName = RefFixture.aChangeRefName();

    Ref aNewRef = zkSharedRefDatabase.newRef(refName, objectId);

    assertThat(aNewRef.getName()).isEqualTo(refName);
    assertThat(aNewRef.getObjectId()).isEqualTo(objectId);
    assertThat(aNewRef.getStorage()).isEqualTo(Storage.NETWORK);
  }

  @Test
  public void shouldCompareAndPutSuccessfully() throws Exception {
    Ref oldRef = aRefObject();
    String projectName = RefFixture.aProjectName();

    marshaller.create(new ZkRefInfo(projectName, oldRef, UUID.randomUUID()));

    assertThat(zkSharedRefDatabase.compareAndPut(projectName, oldRef, aRefObject(oldRef.getName())))
        .isTrue();
  }

  @Test
  public void compareAndPutShouldFailIfTheObjectionHasNotTheExpectedValue() throws Exception {
    String projectName = RefFixture.aProjectName();

    Ref oldRef = aRefObject();
    Ref expectedRef = aRefObject(oldRef.getName());

    marshaller.create(new ZkRefInfo(projectName, oldRef, UUID.randomUUID()));

    assertThat(
            zkSharedRefDatabase.compareAndPut(
                projectName, expectedRef, aRefObject(oldRef.getName())))
        .isFalse();
  }

  @Test
  public void compareAndPutShouldFaiIfTheObjectionDoesNotExist() throws IOException {
    Ref oldRef = aRefObject();
    assertThat(
            zkSharedRefDatabase.compareAndPut(
                RefFixture.aProjectName(), oldRef, aRefObject(oldRef.getName())))
        .isFalse();
  }

  @Test
  public void shouldCompareAndRemoveSuccessfully() throws Exception {
    Ref oldRef = aRefObject();
    String projectName = RefFixture.aProjectName();

    marshaller.create(new ZkRefInfo(projectName, oldRef, UUID.randomUUID()));

    assertThat(zkSharedRefDatabase.compareAndRemove(projectName, oldRef)).isTrue();
  }

  @Test
  public void shouldReplaceTheRefWithATombstoneAfterCompareAndPutRemove() throws Exception {
    Ref oldRef = aRefObject();
    String projectName = RefFixture.aProjectName();

    marshaller.create(new ZkRefInfo(projectName, oldRef, UUID.randomUUID()));

    assertThat(zkSharedRefDatabase.compareAndRemove(projectName, oldRef)).isTrue();

    Optional<ZkRefInfo> inZk = marshaller.read(projectName, oldRef.getName());
    assertThat(inZk.isPresent()).isTrue();
    assertThat(inZk.get().projectName()).isEqualTo(projectName);
    assertThat(inZk.get().refName()).isEqualTo(oldRef.getName());
    assertThat(inZk.get().objectId()).isEqualTo(ObjectId.zeroId());
  }

  @Test
  public void shouldNotCompareAndPutSuccessfullyAfterACompareAndRemove() throws Exception {
    Ref oldRef = aRefObject();
    String projectName = RefFixture.aProjectName();

    marshaller.create(new ZkRefInfo(projectName, oldRef, UUID.randomUUID()));

    zkSharedRefDatabase.compareAndRemove(projectName, oldRef);
    assertThat(zkSharedRefDatabase.compareAndPut(projectName, oldRef, aRefObject(oldRef.getName())))
        .isFalse();
  }
}
