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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.CoreMatchers.nullValue;

import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZkRefInfoDAO.CorruptedZkStorageException;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

public class ZkRefInfoMarshallerTest extends ZookeeperTestContainerSupport implements RefFixture {
  @Rule public ExpectedException expectedException = ExpectedException.none();
  @Rule public TestName nameRule = new TestName();

  @Test
  public void shouldCreateAZRefInfo() throws Exception {
    ZkRefInfo refInfo = aZkRefInfo(AN_OBJECT_ID_1);

    marshaller.create(refInfo);

    Optional<ZkRefInfo> readRefInfo = marshaller.read(refInfo.projectName(), refInfo.refName());

    assertThat(readRefInfo).isEqualTo(Optional.of(refInfo));
  }

  @Test
  public void shouldReturnEmptyIfARefDoesNotExist() throws Exception {
    assertThat(marshaller.read(A_TEST_PROJECT_NAME, aBranchRef())).isEqualTo(Optional.empty());
  }

  @Test
  public void shouldUpdateAZrefInfo() throws Exception {
    ZkRefInfo newRefInfo = aZkRefInfo(AN_OBJECT_ID_1);
    ZkRefInfo updateRefInfo =
        new ZkRefInfo(newRefInfo.projectName(), newRefInfo.refName(), AN_OBJECT_ID_2);

    marshaller.create(newRefInfo);
    marshaller.update(updateRefInfo);

    Optional<ZkRefInfo> readUpdatedRefInfo =
        marshaller.read(updateRefInfo.projectName(), updateRefInfo.refName());

    assertThat(readUpdatedRefInfo).isEqualTo(Optional.of(updateRefInfo));
  }

  @Test
  public void shouldFailToReadZkRefInfoIfSomeOfTheInfoIsMissing() throws Exception {
    String projectName = A_TEST_PROJECT_NAME;
    String refName = aBranchRef();

    curator.createContainers(ZkRefInfoDAO.pathFor(projectName, refName));

    expectedException.expect(CorruptedZkStorageException.class);
    expectedException.expectCause(nullValue(Exception.class));

    marshaller.read(projectName, refName);
  }

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }
}
