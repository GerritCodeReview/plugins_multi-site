// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.googlesource.gerrit.plugins.multisite.ProjectsFilter;
import com.googlesource.gerrit.plugins.multisite.event.EventHandler.EventTask;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.forwarder.StreamEventForwarder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EventHandlerTest {

  private EventHandler eventHandler;

  @Mock private StreamEventForwarder forwarder;
  @Mock private ProjectsFilter projectsFilter;

  @Before
  public void setUp() {
    when(projectsFilter.matches(any(NameKey.class))).thenReturn(true);
    eventHandler =
        new EventHandler(asDynamicSet(forwarder), MoreExecutors.directExecutor(), projectsFilter);
  }

  private DynamicSet<StreamEventForwarder> asDynamicSet(StreamEventForwarder forwarder) {
    DynamicSet<StreamEventForwarder> result = new DynamicSet<>();
    result.add("multi-site", forwarder);
    return result;
  }

  @Test
  public void shouldForwardAnyProjectEvent() throws Exception {
    ProjectEvent event = mock(ProjectEvent.class);
    when(event.getProjectNameKey()).thenReturn(NameKey.parse("test_project"));
    eventHandler.onEvent(event);
    verify(forwarder).send(event);
  }

  @Test
  public void shouldNotForwardNonProjectEvent() throws Exception {
    eventHandler.onEvent(mock(Event.class));
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void shouldNotForwardIfAlreadyForwardedEvent() throws Exception {
    Context.setForwardedEvent(true);
    eventHandler.onEvent(mock(ProjectEvent.class));
    Context.unsetForwardedEvent();
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void shouldNotForwardIfFilteredOutByProjectName() throws Exception {
    when(projectsFilter.matches(any(NameKey.class))).thenReturn(false);

    ProjectEvent event = mock(ProjectEvent.class);
    when(event.getProjectNameKey()).thenReturn(NameKey.parse("test_project"));

    eventHandler.onEvent(event);
    verify(forwarder, never()).send(event);
  }

  @Test
  public void tesEventTaskToString() throws Exception {
    Event event = new RefUpdatedEvent();
    EventTask task = eventHandler.new EventTask(event);
    assertThat(task.toString())
        .isEqualTo(String.format("Send event '%s' to target instance", event.type));
  }
}
