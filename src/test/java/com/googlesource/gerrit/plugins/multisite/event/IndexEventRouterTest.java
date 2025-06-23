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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.util.Providers;
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
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventRouterTest {
  private static final String PLUGIN_NAME = "multi-site";
  private static final String INSTANCE_ID = "instance-id";
  private IndexEventRouter router;
  @Mock private ForwardedIndexAccountHandler indexAccountHandler;
  @Mock private ForwardedIndexChangeHandler indexChangeHandler;
  @Mock private ForwardedIndexGroupHandler indexGroupHandler;
  @Mock private ForwardedIndexProjectHandler indexProjectHandler;
  @Mock private ForwardedEventHandler forwardedEventHandler;
  private AllUsersName allUsersName = new AllUsersName("All-Users");
  PrivateInternals_DynamicMapImpl<ForwardedIndexingHandler<?, ? extends IndexEvent>> indexHandlers;

  @Before
  public void setUp() {
    indexHandlers = (PrivateInternals_DynamicMapImpl) DynamicMap.emptyMap();
    indexHandlers.put(PLUGIN_NAME, AccountIndexEvent.TYPE, Providers.of(indexAccountHandler));
    indexHandlers.put(PLUGIN_NAME, GroupIndexEvent.TYPE, Providers.of(indexGroupHandler));
    indexHandlers.put(PLUGIN_NAME, ChangeIndexEvent.TYPE, Providers.of(indexChangeHandler));
    indexHandlers.put(PLUGIN_NAME, ProjectIndexEvent.TYPE, Providers.of(indexProjectHandler));
    router =
        new IndexEventRouter(
            indexAccountHandler, indexHandlers, PLUGIN_NAME, allUsersName, INSTANCE_ID);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_AccountIndex() throws Exception {
    final AccountIndexEvent event = new AccountIndexEvent(1, INSTANCE_ID);
    router.route(event);

    verify(indexAccountHandler).handle(event);

    verifyNoInteractions(indexChangeHandler, indexGroupHandler, indexProjectHandler);
  }

  @Test
  public void streamEventRouterShouldTriggerAccountIndexFlush() throws Exception {
    StreamEventRouter streamEventRouter = new StreamEventRouter(forwardedEventHandler, router);

    final AccountIndexEvent event = new AccountIndexEvent(1, INSTANCE_ID);
    router.route(event);

    verify(indexAccountHandler).handle(event);

    verifyNoInteractions(indexChangeHandler, indexGroupHandler, indexProjectHandler);

    streamEventRouter.route(new RefReplicationDoneEvent(allUsersName.get(), "refs/any", 1));

    verify(indexAccountHandler).doAsyncIndex();
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_GroupIndex() throws Exception {
    final String groupId = "12";
    final GroupIndexEvent event = new GroupIndexEvent(groupId, ObjectId.zeroId(), INSTANCE_ID);
    router.route(event);

    verify(indexGroupHandler).handle(event);

    verifyNoInteractions(indexAccountHandler, indexChangeHandler, indexProjectHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ProjectIndex() throws Exception {
    final String projectName = "projectName";
    final ProjectIndexEvent event = new ProjectIndexEvent(projectName, INSTANCE_ID);
    router.route(event);

    verify(indexProjectHandler).handle(event);

    verifyNoInteractions(indexAccountHandler, indexChangeHandler, indexGroupHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ChangeIndex() throws Exception {
    final ChangeIndexEvent event = new ChangeIndexEvent("projectName", 3, false, INSTANCE_ID);
    router.route(event);

    verify(indexChangeHandler).handle(event);

    verifyNoInteractions(indexAccountHandler, indexGroupHandler, indexProjectHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ChangeIndexDelete() throws Exception {
    final ChangeIndexEvent event = new ChangeIndexEvent("projectName", 3, true, INSTANCE_ID);
    router.route(event);

    verify(indexChangeHandler).handle(event);

    verifyNoInteractions(indexAccountHandler, indexGroupHandler, indexProjectHandler);
  }

  @Test
  public void routerShouldIgnoreNotRecognisedEvents() throws Exception {
    final IndexEvent newEventType = new IndexEvent("new-type", INSTANCE_ID) {};

    router.route(newEventType);
    verifyNoInteractions(
        indexAccountHandler, indexChangeHandler, indexGroupHandler, indexProjectHandler);
  }
}
