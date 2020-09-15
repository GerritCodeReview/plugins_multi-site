// Copyright (C) 2020 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.testing.GerritJUnit;
import com.googlesource.gerrit.plugins.multisite.Configuration.Projects;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectsFilterTest {

  @Mock private Configuration configuration;
  @Mock private Projects projects;

  private ProjectsFilter objectUnderTest;

  @Test
  public void shouldMatchByExactProjectName() {
    when(projects.getPatterns()).thenReturn(Lists.newArrayList("test_project"));
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    assertThat(objectUnderTest.matches(NameKey.parse("test_project"))).isTrue();
    assertThat(objectUnderTest.matches(NameKey.parse("test_project2"))).isFalse();
  }

  @Test
  public void shouldMatchByWildcard() {
    when(projects.getPatterns()).thenReturn(Lists.newArrayList("test_project*"));
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    assertThat(objectUnderTest.matches(NameKey.parse("test_project"))).isTrue();
    assertThat(objectUnderTest.matches(NameKey.parse("test_project2"))).isTrue();
    assertThat(objectUnderTest.matches(NameKey.parse("2test_project"))).isFalse();
  }

  @Test
  public void shouldMatchByRegex() {
    when(projects.getPatterns()).thenReturn(Lists.newArrayList("^test_(project|project2)"));
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    assertThat(objectUnderTest.matches(NameKey.parse("test_project"))).isTrue();
    assertThat(objectUnderTest.matches(NameKey.parse("test_project2"))).isTrue();
    assertThat(objectUnderTest.matches(NameKey.parse("test_project3"))).isFalse();
  }

  @Test
  public void shouldExcludeByRegex() {
    when(projects.getPatterns()).thenReturn(Lists.newArrayList("^(?:(?!test_project3).)*$"));
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    assertThat(objectUnderTest.matches(NameKey.parse("test_project"))).isTrue();
    assertThat(objectUnderTest.matches(NameKey.parse("test_project2"))).isTrue();
    assertThat(objectUnderTest.matches(NameKey.parse("test_project3"))).isFalse();
  }

  @Test
  public void shouldExcludeByMultipleProjectsRegexPattern() {
    when(projects.getPatterns())
        .thenReturn(Lists.newArrayList("^(?:(?!(test_project3|test_project4)).)*$"));
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    assertThat(objectUnderTest.matches(NameKey.parse("test_project"))).isTrue();
    assertThat(objectUnderTest.matches(NameKey.parse("test_project2"))).isTrue();
    assertThat(objectUnderTest.matches(NameKey.parse("test_project3"))).isFalse();
    assertThat(objectUnderTest.matches(NameKey.parse("test_project4"))).isFalse();
  }

  @Test
  public void shouldMatchWhenNoPatternProvided() {
    when(projects.getPatterns()).thenReturn(Collections.emptyList());
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    assertThat(objectUnderTest.matches(NameKey.parse("test_project"))).isTrue();
  }

  @Test
  public void shouldMatchProjectEvent() {
    ProjectEvent event = mock(ProjectEvent.class);
    when(event.getProjectNameKey()).thenReturn(NameKey.parse("test_project"));
    when(projects.getPatterns()).thenReturn(Lists.newArrayList("test_project"));
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    assertThat(objectUnderTest.matches(event)).isTrue();
  }

  @Test
  public void shouldMatchRefUpdatedEvent() {
    RefUpdatedEvent event = mock(RefUpdatedEvent.class);
    when(event.getProjectNameKey()).thenReturn(NameKey.parse("test_project"));
    when(projects.getPatterns()).thenReturn(Lists.newArrayList("test_project"));
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    assertThat(objectUnderTest.matches(event)).isTrue();
  }

  @Test
  public void shouldExcludedNonProjectEvents() {
    Event event = mock(Event.class);
    when(projects.getPatterns()).thenReturn(Lists.newArrayList("test_project)"));
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    assertThat(objectUnderTest.matches(event)).isFalse();
  }

  @Test
  public void shouldThrowExceptionWhenProjecNameIsNull() {
    when(projects.getPatterns()).thenReturn(Collections.emptyList());
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    GerritJUnit.assertThrows(
        IllegalArgumentException.class, () -> objectUnderTest.matches((NameKey) null));
  }

  @Test
  public void shouldThrowExceptionWhenProjecNameIsEmpty() {
    when(projects.getPatterns()).thenReturn(Collections.emptyList());
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    GerritJUnit.assertThrows(
        IllegalArgumentException.class, () -> objectUnderTest.matches(NameKey.parse("")));
  }

  @Test
  public void shouldThrowExceptionWhenEventIsNull() {
    when(projects.getPatterns()).thenReturn(Collections.emptyList());
    when(configuration.projects()).thenReturn(projects);

    objectUnderTest = new ProjectsFilter(configuration);

    GerritJUnit.assertThrows(
        IllegalArgumentException.class, () -> objectUnderTest.matches((Event) null));
  }
}
