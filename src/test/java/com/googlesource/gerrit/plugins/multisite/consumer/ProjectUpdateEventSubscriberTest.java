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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.events.Event;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ProjectListUpdateRouter;
import java.util.List;

public class ProjectUpdateEventSubscriberTest extends AbstractSubscriberTestBase {

  @SuppressWarnings("rawtypes")
  @Override
  protected ForwardedEventRouter eventRouter() {
    return mock(ProjectListUpdateRouter.class);
  }

  @Override
  protected List<Event> events() {
    return ImmutableList.of(new ProjectListUpdateEvent(PROJECT_NAME, false, INSTANCE_ID));
  }

  @Override
  protected AbstractSubcriber objectUnderTest() {
    return new ProjectUpdateEventSubscriber(
        (ProjectListUpdateRouter) eventRouter,
        asDynamicSet(droppedEventListeners),
        NODE_INSTANCE_ID,
        msgLog,
        subscriberMetrics,
        cfg,
        projectsFilter);
  }
}
