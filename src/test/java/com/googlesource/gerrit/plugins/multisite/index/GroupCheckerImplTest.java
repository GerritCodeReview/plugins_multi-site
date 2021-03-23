// Copyright (C) 2021 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.index;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;

@RunWith(MockitoJUnitRunner.class)
public class GroupCheckerImplTest {
  ObjectId AN_OBJECT_ID = ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  ObjectId ANOTHER_OBJECT_ID = ObjectId.fromString("aabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

  GroupCheckerImpl objectUnderTest;
  @Mock private GitRepositoryManager repoManagerMock;
  @Mock private RefDatabase refDatabaseMock;
  @Mock private Repository repoMock;

  @Before
  public void setUp() throws Exception {
    AllUsersName allUsers = new AllUsersName(AllUsersNameProvider.DEFAULT);
    doReturn(repoMock).when(repoManagerMock).openRepository(allUsers);
    doReturn(refDatabaseMock).when(repoMock).getRefDatabase();
    objectUnderTest = new GroupCheckerImpl(repoManagerMock, allUsers);
  }

  @Test
  public void isGroupUpToDate_shouldReturnTrueWhenEventIsEmpty() {
    assertThat(objectUnderTest.isGroupUpToDate(Optional.empty())).isTrue();
  }

  @Test
  public void isGroupUpToDate_shouldReturnFalseWhenGroupDoesNotExist() {
    assertThat(objectUnderTest.isGroupUpToDate(groupIndexEvent(UUID.randomUUID().toString(), null)))
        .isFalse();
  }

  @Test
  public void isGroupUpToDate_shouldReturnTrueWhenGroupExistsAndSha1IsNull() throws IOException {
    UUID groupUUID = UUID.randomUUID();
    setupExactRefInGroup(groupUUID, AN_OBJECT_ID);

    assertThat(objectUnderTest.isGroupUpToDate(groupIndexEvent(groupUUID.toString(), null)))
        .isTrue();
  }

  @Test
  public void isGroupUpToDate_shouldReturnTrueWhenGroupExistsAndSha1MatchesTheGroupRef()
      throws IOException {
    UUID groupUUID = UUID.randomUUID();
    setupExactRefInGroup(groupUUID, AN_OBJECT_ID);

    assertThat(objectUnderTest.isGroupUpToDate(groupIndexEvent(groupUUID.toString(), AN_OBJECT_ID)))
        .isTrue();
  }

  @Test
  public void isGroupUpToDate_shouldReturnFalseWhenGroupExistsAndSha1DoesNotMatchTheGroupRef()
      throws IOException {
    UUID groupUUID = UUID.randomUUID();
    setupExactRefInGroup(groupUUID, AN_OBJECT_ID);

    assertThat(
            objectUnderTest.isGroupUpToDate(
                groupIndexEvent(groupUUID.toString(), ANOTHER_OBJECT_ID)))
        .isFalse();
  }

  private Optional<GroupIndexEvent> groupIndexEvent(String uuid, @Nullable ObjectId sha1) {
    return Optional.of(new GroupIndexEvent(uuid, sha1));
  }

  private void setupExactRefInGroup(UUID groupUUID, ObjectId objectId) throws IOException {
    String groupRefName = RefNames.refsGroups(AccountGroup.uuid(groupUUID.toString()));
    ObjectIdRef.Unpeeled aRef = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, groupRefName, objectId);

    doReturn(aRef).when(refDatabaseMock).exactRef(groupRefName);
  }
}
