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

import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class RefSharedDatabaseTest implements RefFixture {
  @Rule public TestName nameRule = new TestName();

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }

  @Test
  public void shouldCreateANewRef() {

    ObjectId objectId = AN_OBJECT_ID_1;
    String refName = aBranchRef();

    Ref aNewRef = SharedRefDatabase.newRef(refName, objectId);

    assertThat(aNewRef.getName()).isEqualTo(refName);
    assertThat(aNewRef.getObjectId()).isEqualTo(objectId);
    assertThat(aNewRef.getStorage()).isEqualTo(Storage.NETWORK);
  }
}
