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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gwtorm.server.OrmException;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.index.TestGroupChecker;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedIndexGroupHandlerTest {

  @Rule public ExpectedException exception = ExpectedException.none();
  @Mock private GroupIndexer indexerMock;
  @Mock private ScheduledExecutorService indexExecutorMock;
  @Mock private Configuration config;
  @Mock private Configuration.Index index;
  private ForwardedIndexGroupHandler handler;
  private String uuid;
  private static final int RETRY_INTERVAL = 1000;
  private static final int MAX_TRIES = 2;

  @Before
  public void setUp() throws Exception {
    when(config.index()).thenReturn(index);
    when(index.numStripedLocks()).thenReturn(10);
    when(index.retryInterval()).thenReturn(RETRY_INTERVAL);
    when(index.maxTries()).thenReturn(MAX_TRIES);
    handler = groupHandler(true);
    uuid = "123";
  }

  @Test
  public void testSuccessfulIndexing() throws Exception {
    handler.index(uuid, Operation.INDEX, Optional.empty());
    verify(indexerMock).index(new AccountGroup.UUID(uuid));
  }

  @Test
  public void deleteIsNotSupported() throws Exception {
    exception.expect(UnsupportedOperationException.class);
    exception.expectMessage("Delete from group index not supported");
    handler.index(uuid, Operation.DELETE, Optional.empty());
  }

  @Test
  public void shouldSetAndUnsetForwardedContext() throws Exception {
    // this doAnswer is to allow to assert that context is set to forwarded
    // while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(indexerMock)
        .index(new AccountGroup.UUID(uuid));

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.index(uuid, Operation.INDEX, Optional.empty());
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(new AccountGroup.UUID(uuid));
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrown() throws Exception {
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new IOException("someMessage");
                })
        .when(indexerMock)
        .index(new AccountGroup.UUID(uuid));

    assertThat(Context.isForwardedEvent()).isFalse();
    try {
      handler.index(uuid, Operation.INDEX, Optional.empty());
      fail("should have thrown an IOException");
    } catch (IOException e) {
      assertThat(e.getMessage()).isEqualTo("someMessage");
    }
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(new AccountGroup.UUID(uuid));
  }

  @Test
  public void shouldChangeIndexEventWheNotUpToDate() throws IOException, OrmException {
    ForwardedIndexGroupHandler groupHandlerWithOutdatedEvent = groupHandler(false);
    groupHandlerWithOutdatedEvent.index(uuid, Operation.INDEX, groupIndexEvent(uuid));
    verify(indexerMock).index(new AccountGroup.UUID(uuid));
  }

  @Test
  public void shouldRescheduleGroupIndexingWhenItIsNotUpToDate() throws IOException, OrmException {
    ForwardedIndexGroupHandler groupHandlerWithOutdatedEvent = groupHandler(false);
    groupHandlerWithOutdatedEvent.index(uuid, Operation.INDEX, groupIndexEvent(uuid));
    verify(indexExecutorMock)
        .schedule(any(Runnable.class), eq(new Long(RETRY_INTERVAL)), eq(TimeUnit.MILLISECONDS));
  }

  private ForwardedIndexGroupHandler groupHandler(boolean checkIsUpToDate) {
    return new ForwardedIndexGroupHandler(
        indexerMock, config, new TestGroupChecker(checkIsUpToDate), indexExecutorMock);
  }

  private Optional<GroupIndexEvent> groupIndexEvent(String uuid) {
    return Optional.of(new GroupIndexEvent(uuid, null));
  }
}
