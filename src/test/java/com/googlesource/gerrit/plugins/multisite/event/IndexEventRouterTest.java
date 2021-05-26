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

package com.googlesource.gerrit.plugins.multisite.event;

import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.config.AllUsersName;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedEventHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexAccountHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexChangeHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexGroupHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexProjectHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.IndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.IndexEventRouter;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.StreamEventRouter;
import com.googlesource.gerrit.plugins.replication.events.RefReplicationDoneEvent;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventRouterTest {
  private static final String INSTANCE_ID = "instance-id";
  private IndexEventRouter router;
  @Mock private ForwardedIndexAccountHandler indexAccountHandler;
  @Mock private ForwardedIndexChangeHandler indexChangeHandler;
  @Mock private ForwardedIndexGroupHandler indexGroupHandler;
  @Mock private ForwardedIndexProjectHandler indexProjectHandler;
  @Mock private ForwardedEventHandler forwardedEventHandler;
  private AllUsersName allUsersName = new AllUsersName("All-Users");

  @Before
  public void setUp() {
    router =
        new IndexEventRouter(
            indexAccountHandler,
            indexChangeHandler,
            indexGroupHandler,
            indexProjectHandler,
            allUsersName);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_AccountIndex() throws Exception {
    final AccountIndexEvent event = new AccountIndexEvent(1, INSTANCE_ID);
    router.route(event);

    verify(indexAccountHandler)
        .indexAsync(Account.id(event.accountId), ForwardedIndexingHandler.Operation.INDEX);

    verifyZeroInteractions(indexChangeHandler, indexGroupHandler, indexProjectHandler);
  }

  @Test
  public void streamEventRouterShouldTriggerAccountIndexFlush() throws Exception {

    StreamEventRouter streamEventRouter = new StreamEventRouter(forwardedEventHandler, router);

    final AccountIndexEvent event = new AccountIndexEvent(1, INSTANCE_ID);
    router.route(event);

    verify(indexAccountHandler)
        .indexAsync(Account.id(event.accountId), ForwardedIndexingHandler.Operation.INDEX);

    verifyZeroInteractions(indexChangeHandler, indexGroupHandler, indexProjectHandler);

    streamEventRouter.route(new RefReplicationDoneEvent(allUsersName.get(), "refs/any", 1));

    verify(indexAccountHandler).doAsyncIndex();
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_GroupIndex() throws Exception {
    final String groupId = "12";
    final GroupIndexEvent event = new GroupIndexEvent(groupId, ObjectId.zeroId(), INSTANCE_ID);
    router.route(event);

    verify(indexGroupHandler)
        .index(groupId, ForwardedIndexingHandler.Operation.INDEX, Optional.of(event));

    verifyZeroInteractions(indexAccountHandler, indexChangeHandler, indexProjectHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ProjectIndex() throws Exception {
    final String projectName = "projectName";
    final ProjectIndexEvent event = new ProjectIndexEvent(projectName, INSTANCE_ID);
    router.route(event);

    verify(indexProjectHandler)
        .index(projectName, ForwardedIndexingHandler.Operation.INDEX, Optional.of(event));

    verifyZeroInteractions(indexAccountHandler, indexChangeHandler, indexGroupHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ChangeIndex() throws Exception {
    final ChangeIndexEvent event = new ChangeIndexEvent("projectName", 3, false, INSTANCE_ID);
    router.route(event);

    verify(indexChangeHandler)
        .index(
            event.projectName + "~" + event.changeId,
            ForwardedIndexingHandler.Operation.INDEX,
            Optional.of(event));

    verifyZeroInteractions(indexAccountHandler, indexGroupHandler, indexProjectHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ChangeIndexDelete() throws Exception {
    final ChangeIndexEvent event = new ChangeIndexEvent("projectName", 3, true, INSTANCE_ID);
    router.route(event);

    verify(indexChangeHandler)
        .index(
            event.projectName + "~" + event.changeId,
            ForwardedIndexingHandler.Operation.DELETE,
            Optional.of(event));

    verifyZeroInteractions(indexAccountHandler, indexGroupHandler, indexProjectHandler);
  }

  @Test
  public void routerShouldFailForNotRecognisedEvents() throws Exception {
    final IndexEvent newEventType = new IndexEvent("new-type", INSTANCE_ID) {};

    assertThrows(UnsupportedOperationException.class, () -> router.route(newEventType));
    verifyZeroInteractions(
        indexAccountHandler, indexChangeHandler, indexGroupHandler, indexProjectHandler);
  }
}
