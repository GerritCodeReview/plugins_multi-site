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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.index.ChangeChecker;
import com.googlesource.gerrit.plugins.multisite.index.ChangeCheckerImpl;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedIndexChangeHandlerTest {

  private static final int TEST_CHANGE_NUMBER = 123;
  private static String TEST_PROJECT = "test/project";
  private static String TEST_CHANGE_ID = TEST_PROJECT + "~" + TEST_CHANGE_NUMBER;
  private static final boolean CHANGE_EXISTS = true;
  private static final boolean CHANGE_DOES_NOT_EXIST = false;
  private static final boolean DO_NOT_THROW_STORAGE_EXCEPTION = false;
  private static final boolean THROW_STORAGE_EXCEPTION = true;
  private static final boolean CHANGE_UP_TO_DATE = true;
  private static final boolean CHANGE_OUTDATED = false;
  private static final boolean CHANGE_CONSISTENT = true;
  private static final boolean CHANGE_INCONSISTENT = false;

  @Rule public ExpectedException exception = ExpectedException.none();
  @Mock private ChangeIndexer indexerMock;
  @Mock private OneOffRequestContext ctxMock;
  @Mock private ManualRequestContext manualRequestContextMock;
  @Mock private ChangeNotes changeNotes;
  @Mock private Configuration configurationMock;
  @Mock private Configuration.Index index;
  @Mock private ScheduledExecutorService indexExecutorMock;
  @Mock private GitRepositoryManager gitRepoMgrMock;
  @Mock private ChangeCheckerImpl.Factory changeCheckerFactoryMock;
  @Mock private ChangeChecker changeCheckerAbsentMock;
  @Mock private ChangeChecker changeCheckerPresentMock;
  private ForwardedIndexChangeHandler handler;
  private Change.Id id;
  private Change change;

  @Before
  public void setUp() throws Exception {
    when(ctxMock.open()).thenReturn(manualRequestContextMock);
    id = Change.id(TEST_CHANGE_NUMBER);
    change = new Change(null, id, null, null, TimeUtil.now());
    when(changeNotes.getChange()).thenReturn(change);
    when(changeCheckerFactoryMock.create(any())).thenReturn(changeCheckerAbsentMock);
    when(configurationMock.index()).thenReturn(index);
    when(index.numStripedLocks()).thenReturn(10);
    when(index.maxTries()).thenReturn(1);
    handler =
        new ForwardedIndexChangeHandler(
            indexerMock, configurationMock, indexExecutorMock, ctxMock, changeCheckerFactoryMock);
  }

  @Test
  public void changeIsIndexedWhenUpToDate() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_EXISTS, CHANGE_UP_TO_DATE, CHANGE_CONSISTENT);
    handler.index(TEST_CHANGE_ID, Operation.INDEX, Optional.empty());
    verify(indexerMock, times(1)).index(any(ChangeNotes.class));
  }

  @Test
  public void changeIsStillIndexedEvenWhenOutdated() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_EXISTS, CHANGE_OUTDATED, CHANGE_CONSISTENT);
    handler.index(
        TEST_CHANGE_ID,
        Operation.INDEX,
        Optional.of(new ChangeIndexEvent("foo", 1, false, "instance-id")));
    verify(indexerMock, times(1)).index(any(ChangeNotes.class));
  }

  @Test
  public void changeIsIndexeAtFirstRetryWhenInitiallyInconsistent() throws Exception {
    setupChangeAccessRelatedMocks(
        CHANGE_EXISTS,
        DO_NOT_THROW_STORAGE_EXCEPTION,
        CHANGE_UP_TO_DATE,
        CHANGE_INCONSISTENT,
        CHANGE_CONSISTENT);
    handler.index(
        TEST_CHANGE_ID,
        Operation.INDEX,
        Optional.of(new ChangeIndexEvent("foo", 1, false, "instance-id")));
    verify(indexerMock, never()).index(any(ChangeNotes.class));
    verify(indexExecutorMock, times(1)).schedule(any(Runnable.class), anyLong(), any());

    handler.index(
        TEST_CHANGE_ID,
        Operation.INDEX,
        Optional.of(new ChangeIndexEvent("foo", 1, false, "instance-id")));
    verify(indexerMock, times(1)).index(any(ChangeNotes.class));
  }

  @Test
  public void changeIsDeletedFromIndex() throws Exception {
    handler.index(TEST_CHANGE_ID, Operation.DELETE, Optional.empty());
    verify(indexerMock, times(1)).delete(id);
  }

  @Test
  public void changeToIndexDoesNotExist() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_DOES_NOT_EXIST, CHANGE_OUTDATED);
    handler.index(TEST_CHANGE_ID, Operation.INDEX, Optional.empty());
    verify(indexerMock, never()).delete(id);
    verify(indexerMock, never()).index(any(Project.NameKey.class), any(Change.Id.class));
  }

  @Test
  public void indexerThrowsStorageExceptionTryingToIndexChange() throws Exception {
    setupChangeAccessRelatedMocks(
        CHANGE_EXISTS, THROW_STORAGE_EXCEPTION, CHANGE_UP_TO_DATE, CHANGE_CONSISTENT);
    assertThrows(
        StorageException.class,
        () -> handler.index(TEST_CHANGE_ID, Operation.INDEX, Optional.empty()));
  }

  @Test
  public void shouldSetAndUnsetForwardedContext() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_EXISTS, CHANGE_UP_TO_DATE);
    // this doAnswer is to allow to assert that context is set to forwarded
    // while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(indexerMock)
        .index(any(ChangeNotes.class));

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.index(TEST_CHANGE_ID, Operation.INDEX, Optional.empty());
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock, times(1)).index(any(ChangeNotes.class));
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrown() throws Exception {
    setupChangeAccessRelatedMocks(CHANGE_EXISTS, CHANGE_UP_TO_DATE);
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new IOException("someMessage");
                })
        .when(indexerMock)
        .index(any(ChangeNotes.class));

    assertThat(Context.isForwardedEvent()).isFalse();
    IOException thrown =
        assertThrows(
            IOException.class,
            () -> handler.index(TEST_CHANGE_ID, Operation.INDEX, Optional.empty()));
    assertThat(thrown).hasMessageThat().isEqualTo("someMessage");
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock, times(1)).index(any(ChangeNotes.class));
  }

  private void setupChangeAccessRelatedMocks(boolean changeExist, boolean changeUpToDate)
      throws Exception {
    setupChangeAccessRelatedMocks(
        changeExist, DO_NOT_THROW_STORAGE_EXCEPTION, changeUpToDate, CHANGE_CONSISTENT);
  }

  private void setupChangeAccessRelatedMocks(
      boolean changeExist, boolean changeUpToDate, boolean changeConsistent) throws Exception {
    setupChangeAccessRelatedMocks(
        changeExist, DO_NOT_THROW_STORAGE_EXCEPTION, changeUpToDate, changeConsistent);
  }

  private void setupChangeAccessRelatedMocks(
      boolean changeExists,
      boolean storageException,
      boolean changeIsUpToDate,
      boolean... changeConsistentReturnValues)
      throws StorageException {
    if (changeExists) {
      when(changeCheckerFactoryMock.create(TEST_CHANGE_ID)).thenReturn(changeCheckerPresentMock);
      when(changeCheckerPresentMock.getChangeNotes()).thenReturn(Optional.of(changeNotes));
      if (storageException) {
        doThrow(new StorageException("io-error")).when(indexerMock).index(any(ChangeNotes.class));
      }
    }

    when(changeCheckerPresentMock.isUpToDate(any())).thenReturn(changeIsUpToDate);

    OngoingStubbing<Boolean> changeConsistentCall =
        when(changeCheckerPresentMock.isChangeConsistent());
    for (boolean changeConsistent : changeConsistentReturnValues) {
      changeConsistentCall = changeConsistentCall.thenReturn(changeConsistent);
    }
  }
}
