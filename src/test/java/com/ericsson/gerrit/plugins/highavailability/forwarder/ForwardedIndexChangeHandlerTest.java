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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedIndexChangeHandlerTest {

  private static final boolean CHANGE_EXISTS = true;
  private static final boolean CHANGE_DOES_NOT_EXIST = false;
  private static final boolean DO_NOT_THROW_IO_EXCEPTION = false;
  private static final boolean DO_NOT_THROW_ORM_EXCEPTION = false;
  private static final boolean THROW_IO_EXCEPTION = true;
  private static final boolean THROW_ORM_EXCEPTION = true;

  @Rule public ExpectedException exception = ExpectedException.none();
  @Mock private ChangeIndexer indexerMock;
  @Mock private SchemaFactory<ReviewDb> schemaFactoryMock;
  @Mock private ReviewDb dbMock;
  @Mock private ChangeAccess changeAccessMock;
  @Mock private Configuration config;
  @Mock private Configuration.Index index;
  private ForwardedIndexChangeHandler handler;
  private Change.Id id;
  private Change change;

  @Before
  public void setUp() throws Exception {
    when(schemaFactoryMock.open()).thenReturn(dbMock);
    when(dbMock.changes()).thenReturn(changeAccessMock);
    when(config.index()).thenReturn(index);
    when(index.numStripedLocks()).thenReturn(10);
    id = new Change.Id(123);
    change = new Change(null, id, null, null, TimeUtil.nowTs());
    handler = new ForwardedIndexChangeHandler(indexerMock, schemaFactoryMock, config);
  }

  @Test
  public void changeIsIndexed() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_EXISTS);
    handler.index(id, Operation.INDEX);
    verify(indexerMock, times(1)).index(dbMock, change);
  }

  @Test
  public void changeIsDeletedFromIndex() throws Exception {
    handler.index(id, Operation.DELETE);
    verify(indexerMock, times(1)).delete(id);
  }

  @Test
  public void changeToIndexDoesNotExist() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_DOES_NOT_EXIST);
    handler.index(id, Operation.INDEX);
    verify(indexerMock, times(1)).delete(id);
  }

  @Test
  public void schemaThrowsExceptionWhenLookingUpForChange() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_EXISTS, THROW_ORM_EXCEPTION);
    exception.expect(OrmException.class);
    handler.index(id, Operation.INDEX);
  }

  @Test
  public void indexerThrowsNoSuchChangeExceptionTryingToPostChange() throws Exception {
    doThrow(new NoSuchChangeException(id)).when(schemaFactoryMock).open();
    handler.index(id, Operation.INDEX);
    verify(indexerMock, times(1)).delete(id);
  }

  @Test
  public void indexerThrowsNestedNoSuchChangeExceptionTryingToPostChange() throws Exception {
    OrmException e = new OrmException("test", new NoSuchChangeException(id));
    doThrow(e).when(schemaFactoryMock).open();
    handler.index(id, Operation.INDEX);
    verify(indexerMock, times(1)).delete(id);
  }

  @Test
  public void indexerThrowsIOExceptionTryingToIndexChange() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_EXISTS, DO_NOT_THROW_ORM_EXCEPTION, THROW_IO_EXCEPTION);
    exception.expect(IOException.class);
    handler.index(id, Operation.INDEX);
  }

  @Test
  public void shouldSetAndUnsetForwardedContext() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_EXISTS);
    // this doAnswer is to allow to assert that context is set to forwarded
    // while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(indexerMock)
        .index(dbMock, change);

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.index(id, Operation.INDEX);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock, times(1)).index(dbMock, change);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrown() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_EXISTS);
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new IOException("someMessage");
                })
        .when(indexerMock)
        .index(dbMock, change);

    assertThat(Context.isForwardedEvent()).isFalse();
    try {
      handler.index(id, Operation.INDEX);
      fail("should have thrown an IOException");
    } catch (IOException e) {
      assertThat(e.getMessage()).isEqualTo("someMessage");
    }
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock, times(1)).index(dbMock, change);
  }

  private void setupChangeAccessRelatedMocks(boolean changeExist) throws Exception {
    setupChangeAccessRelatedMocks(
        changeExist, DO_NOT_THROW_ORM_EXCEPTION, DO_NOT_THROW_IO_EXCEPTION);
  }

  private void setupChangeAccessRelatedMocks(boolean changeExist, boolean ormException)
      throws OrmException, IOException {
    setupChangeAccessRelatedMocks(changeExist, ormException, DO_NOT_THROW_IO_EXCEPTION);
  }

  private void setupChangeAccessRelatedMocks(
      boolean changeExists, boolean ormException, boolean ioException)
      throws OrmException, IOException {
    if (ormException) {
      doThrow(new OrmException("")).when(schemaFactoryMock).open();
    } else {
      when(schemaFactoryMock.open()).thenReturn(dbMock);
      ChangeAccess ca = mock(ChangeAccess.class);
      when(dbMock.changes()).thenReturn(ca);
      if (changeExists) {
        when(ca.get(id)).thenReturn(change);
        if (ioException) {
          doThrow(new IOException("io-error")).when(indexerMock).index(dbMock, change);
        }
      } else {
        when(ca.get(id)).thenReturn(null);
      }
    }
  }
}
