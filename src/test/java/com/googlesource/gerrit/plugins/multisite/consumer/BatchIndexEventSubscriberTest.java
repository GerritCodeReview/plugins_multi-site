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

package com.googlesource.gerrit.plugins.multisite.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.IndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.IndexEventRouter;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class BatchIndexEventSubscriberTest extends AbstractSubscriberTestBase {
  private static final boolean DELETED = false;
  private static final int CHANGE_ID = 1;

  @SuppressWarnings("unchecked")
  @Test
  public void shouldConsumeNonProjectAndNonChangeIndexingEventsTypes()
      throws IOException, PermissionBackendException, CacheNotFoundException {
    IndexEvent event = new AccountIndexEvent(1, INSTANCE_ID);

    objectUnderTest.getConsumer().accept(event);

    verify(projectsFilter, never()).matches(PROJECT_NAME);
    verify(eventRouter, times(1)).route(event);
    verify(droppedEventListeners, never()).onEventDropped(event);
  }

  @SuppressWarnings("rawtypes")
  @Override
  protected ForwardedEventRouter eventRouter() {
    return mock(IndexEventRouter.class);
  }

  @Override
  protected List<Event> events() {
    return ImmutableList.of(
        new ProjectIndexEvent(PROJECT_NAME, INSTANCE_ID),
        new ChangeIndexEvent(PROJECT_NAME, CHANGE_ID, DELETED, INSTANCE_ID));
  }

  @Override
  protected AbstractSubcriber objectUnderTest() {
    return new BatchIndexEventSubscriber(
        (IndexEventRouter) eventRouter,
        asDynamicSet(droppedEventListeners),
        NODE_INSTANCE_ID,
        msgLog,
        subscriberMetrics,
        cfg,
        projectsFilter);
  }
}
