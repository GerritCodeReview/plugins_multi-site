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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.StreamEventRouter;
import java.io.IOException;
import org.junit.Test;
import org.mockito.Mock;

public class StreamEventSubscriberTest extends AbstractSubscriberTestBase {

  @Mock RefUpdatedEvent event;

  private static final NameKey PROJECT_NAME_KEY = NameKey.parse(PROJECT_NAME);

  StreamEventSubscriber objectUnderTest;

  @Override
  public void setup() {
    super.setup();
    when(event.getProjectNameKey()).thenReturn(PROJECT_NAME_KEY);
    event.instanceId = INSTANCE_ID;
    objectUnderTest =
        new StreamEventSubscriber(
            (StreamEventRouter) eventRouter,
            asDynamicSet(droppedEventListeners),
            INSTANCE_ID,
            msgLog,
            subscriberMetrics,
            cfg,
            projectsFilter);
  }

  @Test
  public void shouldConsumeProjectListUpdateEventWhenNotFilteredByProjectName()
      throws IOException, PermissionBackendException, CacheNotFoundException {
    when(projectsFilter.matches(eq(PROJECT_NAME_KEY))).thenReturn(true);

    objectUnderTest.getConsumer().accept(event);

    verify(projectsFilter, times(1)).matches(PROJECT_NAME_KEY);
    verify(eventRouter, times(1)).route(event);
    verify(droppedEventListeners, never()).onEventDropped(event);
  }

  @Test
  public void shouldSkipProjectListUpdateEventWhenFilteredByProjectName()
      throws IOException, PermissionBackendException, CacheNotFoundException {
    when(projectsFilter.matches(eq(PROJECT_NAME_KEY))).thenReturn(false);

    objectUnderTest.getConsumer().accept(event);

    verify(projectsFilter, times(1)).matches(PROJECT_NAME_KEY);
    verify(eventRouter, never()).route(event);
    verify(droppedEventListeners, times(1)).onEventDropped(event);
  }

  @Override
  protected ForwardedEventRouter eventRouter() {
    return mock(StreamEventRouter.class);
  }
}
