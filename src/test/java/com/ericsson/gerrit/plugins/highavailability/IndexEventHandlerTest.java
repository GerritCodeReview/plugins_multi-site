// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;

import com.ericsson.gerrit.plugins.highavailability.Context;
import com.ericsson.gerrit.plugins.highavailability.IndexEventHandler;
import com.ericsson.gerrit.plugins.highavailability.RestSession;
import com.ericsson.gerrit.plugins.highavailability.IndexEventHandler.SyncIndexTask;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventHandlerTest {
  private static final String PLUGIN_NAME = "sync-index";
  private static final int CHANGE_ID = 1;

  private IndexEventHandler indexEventHandler;
  @Mock
  private RestSession restSessionMock;
  private Change.Id id;

  @BeforeClass
  public static void setUp() {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  @Before
  public void setUpMocks() {
    id = Change.Id.parse(Integer.toString(CHANGE_ID));
    indexEventHandler = new IndexEventHandler(MoreExecutors.directExecutor(),
        PLUGIN_NAME, restSessionMock);
  }

  @Test
  public void shouldIndexInRemoteOnChangeIndexedEvent() throws Exception {
    indexEventHandler.onChangeIndexed(id.get());
    verify(restSessionMock).index(CHANGE_ID);
  }

  @Test
  public void shouldDeleteFromIndexInRemoteOnChangeDeletedEvent()
      throws Exception {
    indexEventHandler.onChangeDeleted(id.get());
    verify(restSessionMock).deleteFromIndex(CHANGE_ID);
  }

  @Test
  public void shouldNotCallRemoteWhenEventIsForwarded() throws Exception {
    Context.setForwardedEvent(true);
    indexEventHandler.onChangeIndexed(id.get());
    indexEventHandler.onChangeDeleted(id.get());
    Context.unsetForwardedEvent();
    verifyZeroInteractions(restSessionMock);
  }

  @Test
  public void duplicateEventOfAQueuedEventShouldGetDiscarded() {
    Executor poolMock = mock(Executor.class);
    indexEventHandler =
        new IndexEventHandler(poolMock, PLUGIN_NAME, restSessionMock);
    indexEventHandler.onChangeIndexed(id.get());
    indexEventHandler.onChangeIndexed(id.get());
    verify(poolMock, times(1))
        .execute(indexEventHandler.new SyncIndexTask(CHANGE_ID, false));
  }

  @Test
  public void testSyncIndexTaskToString() throws Exception {
    SyncIndexTask syncIndexTask =
        indexEventHandler.new SyncIndexTask(CHANGE_ID, false);
    assertThat(syncIndexTask.toString()).isEqualTo(String.format(
        "[%s] Index change %s in target instance", PLUGIN_NAME, CHANGE_ID));
  }

  @Test
  public void testSyncIndexTaskHashCodeAndEquals() {
    SyncIndexTask task = indexEventHandler.new SyncIndexTask(CHANGE_ID, false);

    assertThat(task.equals(task)).isTrue();
    assertThat(task.hashCode()).isEqualTo(task.hashCode());

    SyncIndexTask identicalTask =
        indexEventHandler.new SyncIndexTask(CHANGE_ID, false);
    assertThat(task.equals(identicalTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(identicalTask.hashCode());

    assertThat(task.equals(null)).isFalse();
    assertThat(task.equals("test")).isFalse();
    assertThat(task.hashCode()).isNotEqualTo("test".hashCode());

    SyncIndexTask differentChangeIdTask =
        indexEventHandler.new SyncIndexTask(123, false);
    assertThat(task.equals(differentChangeIdTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(differentChangeIdTask.hashCode());

    SyncIndexTask removeTask =
        indexEventHandler.new SyncIndexTask(CHANGE_ID, true);
    assertThat(task.equals(removeTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(removeTask.hashCode());
  }
}
