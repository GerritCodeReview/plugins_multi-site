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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.permissions.PermissionBackendException;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ProjectListUpdateRouter;
import java.io.IOException;
import org.junit.Test;

public class ProjectUpdateEventSubscriberTest extends AbstractSubscriberTestBase {

  private static final boolean REMOVE = false;

  ProjectUpdateEventSubscriber objectUnderTest;

  @Override
  public void setup() {
    super.setup();
    objectUnderTest =
        new ProjectUpdateEventSubscriber(
            (ProjectListUpdateRouter) eventRouter,
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
    when(projectsFilter.matches(any(String.class))).thenReturn(true);
    ProjectListUpdateEvent event = new ProjectListUpdateEvent(PROJECT_NAME, REMOVE, INSTANCE_ID);

    objectUnderTest.getConsumer().accept(event);

    verifyConsumed(event);
  }

  @Test
  public void shouldSkipProjectListUpdateEventWhenFilteredByProjectName()
      throws IOException, PermissionBackendException, CacheNotFoundException {
    when(projectsFilter.matches(any(String.class))).thenReturn(false);
    ProjectListUpdateEvent event = new ProjectListUpdateEvent(PROJECT_NAME, REMOVE, INSTANCE_ID);

    objectUnderTest.getConsumer().accept(event);

    verifySkipped(event);
  }

  @Override
  protected ForwardedEventRouter eventRouter() {
    return mock(ProjectListUpdateRouter.class);
  }
}
