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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.StreamEventRouter;
import java.util.List;
import org.mockito.Mock;

public class StreamEventSubscriberTest extends AbstractSubscriberTestBase {
  private static final NameKey PROJECT_NAME_KEY = NameKey.parse(PROJECT_NAME);
  private @Mock RefUpdatedEvent event;

  @Override
  protected ForwardedEventRouter eventRouter() {
    return mock(StreamEventRouter.class);
  }

  @Override
  protected List<Event> events() {
    when(event.getProjectNameKey()).thenReturn(PROJECT_NAME_KEY);
    event.instanceId = INSTANCE_ID;
    return ImmutableList.of(event);
  }

  @Override
  protected AbstractSubcriber objectUnderTest() {
    return new StreamEventSubscriber(
        (StreamEventRouter) eventRouter,
        asDynamicSet(droppedEventListeners),
        INSTANCE_ID,
        msgLog,
        subscriberMetrics,
        cfg,
        projectsFilter);
  }
}
