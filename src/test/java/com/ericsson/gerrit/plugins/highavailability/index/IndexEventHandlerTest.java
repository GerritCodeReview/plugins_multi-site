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

package com.ericsson.gerrit.plugins.highavailability.index;

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

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexTask;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventHandlerTest {
  private static final String PLUGIN_NAME = "high-availability";
  private static final int CHANGE_ID = 1;

  private IndexEventHandler indexEventHandler;
  @Mock
  private Forwarder forwarder;
  private Change.Id id;

  @BeforeClass
  public static void setUp() {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  @Before
  public void setUpMocks() {
    id = Change.Id.parse(Integer.toString(CHANGE_ID));
    indexEventHandler = new IndexEventHandler(MoreExecutors.directExecutor(),
        PLUGIN_NAME, forwarder);
  }

  @Test
  public void shouldIndexInRemoteOnChangeIndexedEvent() throws Exception {
    indexEventHandler.onChangeIndexed(id.get());
    verify(forwarder).indexChange(CHANGE_ID);
  }

  @Test
  public void shouldDeleteFromIndexInRemoteOnChangeDeletedEvent()
      throws Exception {
    indexEventHandler.onChangeDeleted(id.get());
    verify(forwarder).deleteChangeFromIndex(CHANGE_ID);
  }

  @Test
  public void shouldNotCallRemoteWhenEventIsForwarded() throws Exception {
    Context.setForwardedEvent(true);
    indexEventHandler.onChangeIndexed(id.get());
    indexEventHandler.onChangeDeleted(id.get());
    Context.unsetForwardedEvent();
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void duplicateEventOfAQueuedEventShouldGetDiscarded() {
    Executor poolMock = mock(Executor.class);
    indexEventHandler =
        new IndexEventHandler(poolMock, PLUGIN_NAME, forwarder);
    indexEventHandler.onChangeIndexed(id.get());
    indexEventHandler.onChangeIndexed(id.get());
    verify(poolMock, times(1))
        .execute(indexEventHandler.new IndexTask(CHANGE_ID, false));
  }

  @Test
  public void testIndexTaskToString() throws Exception {
    IndexTask indexTask =
        indexEventHandler.new IndexTask(CHANGE_ID, false);
    assertThat(indexTask.toString()).isEqualTo(String.format(
        "[%s] Index change %s in target instance", PLUGIN_NAME, CHANGE_ID));
  }

  @Test
  public void testIndexTaskHashCodeAndEquals() {
    IndexTask task = indexEventHandler.new IndexTask(CHANGE_ID, false);

    assertThat(task.equals(task)).isTrue();
    assertThat(task.hashCode()).isEqualTo(task.hashCode());

    IndexTask identicalTask =
        indexEventHandler.new IndexTask(CHANGE_ID, false);
    assertThat(task.equals(identicalTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(identicalTask.hashCode());

    assertThat(task.equals(null)).isFalse();
    assertThat(task.equals("test")).isFalse();
    assertThat(task.hashCode()).isNotEqualTo("test".hashCode());

    IndexTask differentChangeIdTask =
        indexEventHandler.new IndexTask(123, false);
    assertThat(task.equals(differentChangeIdTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(differentChangeIdTask.hashCode());

    IndexTask removeTask =
        indexEventHandler.new IndexTask(CHANGE_ID, true);
    assertThat(task.equals(removeTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(removeTask.hashCode());
  }
}
