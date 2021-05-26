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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedProjectListUpdateHandlerTest {

  private static final String INSTANCE_ID = "instance-id";
  private static final String PROJECT_NAME = "someProject";
  private static final String SOME_MESSAGE = "someMessage";
  private static final Project.NameKey PROJECT_KEY = Project.nameKey(PROJECT_NAME);
  @Rule public ExpectedException exception = ExpectedException.none();
  @Mock private ProjectCache projectCacheMock;
  private ForwardedProjectListUpdateHandler handler;

  @Before
  public void setUp() throws Exception {
    handler = new ForwardedProjectListUpdateHandler(projectCacheMock);
  }

  @Test
  public void testSuccessfulAdd() throws Exception {
    handler.update(new ProjectListUpdateEvent(PROJECT_NAME, false, INSTANCE_ID));
    verify(projectCacheMock).onCreateProject(PROJECT_KEY);
  }

  @Test
  public void testSuccessfulRemove() throws Exception {
    handler.update(new ProjectListUpdateEvent(PROJECT_NAME, true, INSTANCE_ID));
    verify(projectCacheMock).remove(PROJECT_KEY);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextOnAdd() throws Exception {
    // this doAnswer is to allow to assert that context is set to forwarded
    // while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(projectCacheMock)
        .onCreateProject(PROJECT_KEY);

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.update(new ProjectListUpdateEvent(PROJECT_NAME, false, INSTANCE_ID));
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(projectCacheMock).onCreateProject(PROJECT_KEY);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextOnRemove() throws Exception {
    // this doAnswer is to allow to assert that context is set to forwarded
    // while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(projectCacheMock)
        .remove(PROJECT_KEY);

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.update(new ProjectListUpdateEvent(PROJECT_NAME, true, INSTANCE_ID));
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(projectCacheMock).remove(PROJECT_KEY);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrownOnAdd() throws Exception {
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new RuntimeException(SOME_MESSAGE);
                })
        .when(projectCacheMock)
        .onCreateProject(PROJECT_KEY);

    assertThat(Context.isForwardedEvent()).isFalse();
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> handler.update(new ProjectListUpdateEvent(PROJECT_NAME, false, INSTANCE_ID)));
    assertThat(thrown).hasMessageThat().isEqualTo(SOME_MESSAGE);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(projectCacheMock).onCreateProject(PROJECT_KEY);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrownOnRemove() throws Exception {
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new RuntimeException(SOME_MESSAGE);
                })
        .when(projectCacheMock)
        .remove(PROJECT_KEY);

    assertThat(Context.isForwardedEvent()).isFalse();
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> handler.update(new ProjectListUpdateEvent(PROJECT_NAME, true, INSTANCE_ID)));
    assertThat(thrown).hasMessageThat().isEqualTo(SOME_MESSAGE);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(projectCacheMock).remove(PROJECT_KEY);
  }
}
