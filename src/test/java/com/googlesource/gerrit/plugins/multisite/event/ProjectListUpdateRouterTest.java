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

import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedProjectListUpdateHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ProjectListUpdateRouter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectListUpdateRouterTest {

  private ProjectListUpdateRouter router;
  @Mock private ForwardedProjectListUpdateHandler projectListUpdateHandler;

  @Before
  public void setUp() {
    router = new ProjectListUpdateRouter(projectListUpdateHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ProjectListUpdate() throws Exception {
    String instanceId = "instance-id";
    final ProjectListUpdateEvent event = new ProjectListUpdateEvent("project", false, instanceId);
    router.route(event);

    verify(projectListUpdateHandler).update(event);
  }
}
