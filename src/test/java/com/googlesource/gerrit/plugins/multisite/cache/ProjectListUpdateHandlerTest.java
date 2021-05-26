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

package com.googlesource.gerrit.plugins.multisite.cache;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.globalrefdb.validation.ProjectsFilter;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.googlesource.gerrit.plugins.multisite.cache.ProjectListUpdateHandler.ProjectListUpdateTask;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.forwarder.ProjectListUpdateForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectListUpdateHandlerTest {

  private static final String INSTANCE_ID = "instance-id";

  private ProjectListUpdateHandler handler;

  @Mock private ProjectListUpdateForwarder forwarder;
  @Mock private ProjectsFilter projectsFilter;

  @Before
  public void setUp() {
    when(projectsFilter.matches(any(String.class))).thenReturn(true);
    handler =
        new ProjectListUpdateHandler(
            asDynamicSet(forwarder), MoreExecutors.directExecutor(), projectsFilter, INSTANCE_ID);
  }

  private DynamicSet<ProjectListUpdateForwarder> asDynamicSet(
      ProjectListUpdateForwarder forwarder) {
    DynamicSet<ProjectListUpdateForwarder> result = new DynamicSet<>();
    result.add("multi-site", forwarder);
    return result;
  }

  @Test
  public void shouldForwardAddedProject() throws Exception {
    String projectName = "projectToAdd";
    NewProjectCreatedListener.Event event = mock(NewProjectCreatedListener.Event.class);
    when(event.getProjectName()).thenReturn(projectName);
    handler.onNewProjectCreated(event);
    verify(forwarder)
        .updateProjectList(
            any(ProjectListUpdateTask.class),
            eq(new ProjectListUpdateEvent(projectName, false, INSTANCE_ID)));
  }

  @Test
  public void shouldForwardDeletedProject() throws Exception {
    String projectName = "projectToDelete";
    ProjectDeletedListener.Event event = mock(ProjectDeletedListener.Event.class);
    when(event.getProjectName()).thenReturn(projectName);
    handler.onProjectDeleted(event);
    verify(forwarder)
        .updateProjectList(
            any(ProjectListUpdateTask.class),
            eq(new ProjectListUpdateEvent(projectName, true, INSTANCE_ID)));
  }

  @Test
  public void shouldNotForwardIfAlreadyForwardedEvent() throws Exception {
    Context.setForwardedEvent(true);
    handler.onNewProjectCreated(mock(NewProjectCreatedListener.Event.class));
    handler.onProjectDeleted(mock(ProjectDeletedListener.Event.class));
    Context.unsetForwardedEvent();
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void shouldNotForwardIfFilteredOutByProjectName() throws Exception {
    when(projectsFilter.matches(any(String.class))).thenReturn(false);
    String projectName = "projectToAdd";
    NewProjectCreatedListener.Event event = mock(NewProjectCreatedListener.Event.class);
    when(event.getProjectName()).thenReturn(projectName);
    handler.onNewProjectCreated(event);
    verify(forwarder, never())
        .updateProjectList(
            any(ProjectListUpdateTask.class),
            eq(new ProjectListUpdateEvent(projectName, true, INSTANCE_ID)));
  }

  @Test
  public void testProjectUpdateTaskToString() throws Exception {
    String projectName = "someProjectName";
    ProjectListUpdateTask task =
        handler
        .new ProjectListUpdateTask(new ProjectListUpdateEvent(projectName, false, INSTANCE_ID));
    assertThat(task.toString())
        .isEqualTo(String.format("Update project list in target instance: add '%s'", projectName));

    task =
        handler
        .new ProjectListUpdateTask(new ProjectListUpdateEvent(projectName, true, INSTANCE_ID));
    assertThat(task.toString())
        .isEqualTo(
            String.format("Update project list in target instance: remove '%s'", projectName));
  }
}
