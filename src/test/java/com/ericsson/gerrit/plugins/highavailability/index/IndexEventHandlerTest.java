// Copyright (C) 2015 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.index;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexAccountTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexChangeTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexGroupTask;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventHandlerTest {
  private static final String PLUGIN_NAME = "high-availability";
  private static final String PROJECT_NAME = "test/project";
  private static final int CHANGE_ID = 1;
  private static final int ACCOUNT_ID = 2;
  private static final String UUID = "3";
  private static final String OTHER_UUID = "4";

  private IndexEventHandler indexEventHandler;
  @Mock private Forwarder forwarder;
  @Mock private ChangeCheckerImpl.Factory changeCheckerFactoryMock;
  @Mock private ChangeChecker changeCheckerMock;
  private Change.Id changeId;
  private Account.Id accountId;
  private AccountGroup.UUID accountGroupUUID;

  @Before
  public void setUpMocks() throws Exception {
    changeId = new Change.Id(CHANGE_ID);
    accountId = new Account.Id(ACCOUNT_ID);
    accountGroupUUID = new AccountGroup.UUID(UUID);
    when(changeCheckerFactoryMock.create(any())).thenReturn(changeCheckerMock);
    when(changeCheckerMock.newIndexEvent()).thenReturn(Optional.of(new IndexEvent()));
    indexEventHandler =
        new IndexEventHandler(
            MoreExecutors.directExecutor(), PLUGIN_NAME, forwarder, changeCheckerFactoryMock);
  }

  @Test
  public void shouldIndexInRemoteOnChangeIndexedEvent() throws Exception {
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    verify(forwarder).indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any());
  }

  @Test
  public void shouldIndexInRemoteOnAccountIndexedEvent() throws Exception {
    indexEventHandler.onAccountIndexed(accountId.get());
    verify(forwarder).indexAccount(eq(ACCOUNT_ID), any());
  }

  @Test
  public void shouldDeleteFromIndexInRemoteOnChangeDeletedEvent() throws Exception {
    indexEventHandler.onChangeDeleted(changeId.get());
    verify(forwarder).deleteChangeFromIndex(eq(CHANGE_ID), any());
  }

  @Test
  public void shouldIndexInRemoteOnGroupIndexedEvent() throws Exception {
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    verify(forwarder).indexGroup(eq(UUID), any());
  }

  @Test
  public void shouldNotCallRemoteWhenChangeEventIsForwarded() throws Exception {
    Context.setForwardedEvent(true);
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    indexEventHandler.onChangeDeleted(changeId.get());
    Context.unsetForwardedEvent();
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void shouldNotCallRemoteWhenAccountEventIsForwarded() throws Exception {
    Context.setForwardedEvent(true);
    indexEventHandler.onAccountIndexed(accountId.get());
    indexEventHandler.onAccountIndexed(accountId.get());
    Context.unsetForwardedEvent();
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void shouldNotCallRemoteWhenGroupEventIsForwarded() throws Exception {
    Context.setForwardedEvent(true);
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    Context.unsetForwardedEvent();
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void duplicateChangeEventOfAQueuedEventShouldGetDiscarded() {
    ScheduledThreadPoolExecutor poolMock = mock(ScheduledThreadPoolExecutor.class);
    indexEventHandler =
        new IndexEventHandler(poolMock, PLUGIN_NAME, forwarder, changeCheckerFactoryMock);
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    verify(poolMock, times(1))
        .execute(indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, false, null));
  }

  @Test
  public void duplicateAccountEventOfAQueuedEventShouldGetDiscarded() {
    ScheduledThreadPoolExecutor poolMock = mock(ScheduledThreadPoolExecutor.class);
    indexEventHandler =
        new IndexEventHandler(poolMock, PLUGIN_NAME, forwarder, changeCheckerFactoryMock);
    indexEventHandler.onAccountIndexed(accountId.get());
    indexEventHandler.onAccountIndexed(accountId.get());
    verify(poolMock, times(1)).execute(indexEventHandler.new IndexAccountTask(ACCOUNT_ID));
  }

  @Test
  public void duplicateGroupEventOfAQueuedEventShouldGetDiscarded() {
    ScheduledThreadPoolExecutor poolMock = mock(ScheduledThreadPoolExecutor.class);
    indexEventHandler =
        new IndexEventHandler(poolMock, PLUGIN_NAME, forwarder, changeCheckerFactoryMock);
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    verify(poolMock, times(1)).execute(indexEventHandler.new IndexGroupTask(UUID));
  }

  @Test
  public void testIndexChangeTaskToString() throws Exception {
    IndexChangeTask task =
        indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, false, null);
    assertThat(task.toString())
        .isEqualTo(
            String.format("[%s] Index change %s in target instance", PLUGIN_NAME, CHANGE_ID));
  }

  @Test
  public void testIndexAccountTaskToString() throws Exception {
    IndexAccountTask task = indexEventHandler.new IndexAccountTask(ACCOUNT_ID);
    assertThat(task.toString())
        .isEqualTo(
            String.format("[%s] Index account %s in target instance", PLUGIN_NAME, ACCOUNT_ID));
  }

  @Test
  public void testIndexGroupTaskToString() throws Exception {
    IndexGroupTask task = indexEventHandler.new IndexGroupTask(UUID);
    assertThat(task.toString())
        .isEqualTo(String.format("[%s] Index group %s in target instance", PLUGIN_NAME, UUID));
  }

  @Test
  public void testIndexChangeTaskHashCodeAndEquals() {
    IndexChangeTask task =
        indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, false, null);

    IndexChangeTask sameTask = task;
    assertThat(task.equals(sameTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(sameTask.hashCode());

    IndexChangeTask identicalTask =
        indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, false, null);
    assertThat(task.equals(identicalTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(identicalTask.hashCode());

    assertThat(task.equals(null)).isFalse();
    assertThat(
            task.equals(
                indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID + 1, false, null)))
        .isFalse();
    assertThat(task.hashCode()).isNotEqualTo("test".hashCode());

    IndexChangeTask differentChangeIdTask =
        indexEventHandler.new IndexChangeTask(PROJECT_NAME, 123, false, null);
    assertThat(task.equals(differentChangeIdTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(differentChangeIdTask.hashCode());

    IndexChangeTask removeTask = indexEventHandler.new IndexChangeTask("", CHANGE_ID, true, null);
    assertThat(task.equals(removeTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(removeTask.hashCode());
  }

  @Test
  public void testIndexAccountTaskHashCodeAndEquals() {
    IndexAccountTask task = indexEventHandler.new IndexAccountTask(ACCOUNT_ID);

    IndexAccountTask sameTask = task;
    assertThat(task.equals(sameTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(sameTask.hashCode());

    IndexAccountTask identicalTask = indexEventHandler.new IndexAccountTask(ACCOUNT_ID);
    assertThat(task.equals(identicalTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(identicalTask.hashCode());

    assertThat(task.equals(null)).isFalse();
    assertThat(task.equals(indexEventHandler.new IndexAccountTask(ACCOUNT_ID + 1))).isFalse();
    assertThat(task.hashCode()).isNotEqualTo("test".hashCode());

    IndexAccountTask differentAccountIdTask = indexEventHandler.new IndexAccountTask(123);
    assertThat(task.equals(differentAccountIdTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(differentAccountIdTask.hashCode());
  }

  @Test
  public void testIndexGroupTaskHashCodeAndEquals() {
    IndexGroupTask task = indexEventHandler.new IndexGroupTask(UUID);

    IndexGroupTask sameTask = task;
    assertThat(task.equals(sameTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(sameTask.hashCode());

    IndexGroupTask identicalTask = indexEventHandler.new IndexGroupTask(UUID);
    assertThat(task.equals(identicalTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(identicalTask.hashCode());

    assertThat(task.equals(null)).isFalse();
    assertThat(task.equals(indexEventHandler.new IndexGroupTask(OTHER_UUID))).isFalse();
    assertThat(task.hashCode()).isNotEqualTo("test".hashCode());

    IndexGroupTask differentGroupIdTask = indexEventHandler.new IndexGroupTask("123");
    assertThat(task.equals(differentGroupIdTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(differentGroupIdTask.hashCode());
  }
}
