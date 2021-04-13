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

package com.googlesource.gerrit.plugins.multisite.index;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.globalrefdb.validation.ProjectsFilter;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import com.googlesource.gerrit.plugins.multisite.index.IndexEventHandler.IndexProjectTask;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventHandlerTest {

  private IndexEventHandler eventHandler;

  @Mock private ProjectsFilter projectsFilter;
  @Mock private IndexEventForwarder forwarder;
  @Mock private ChangeCheckerImpl.Factory changeChecker;

  @Before
  public void setUp() {
    eventHandler =
        new IndexEventHandler(
            MoreExecutors.directExecutor(),
            asDynamicSet(forwarder),
            changeChecker,
            projectsFilter,
            new TestGroupChecker(true));
  }

  private DynamicSet<IndexEventForwarder> asDynamicSet(IndexEventForwarder forwarder) {
    DynamicSet<IndexEventForwarder> result = new DynamicSet<>();
    result.add("multi-site", forwarder);
    return result;
  }

  @Test
  public void shouldNotForwardProjectIndexedIfFilteredOutByProjectName() throws Exception {
    when(projectsFilter.matches(any(String.class))).thenReturn(false);

    eventHandler.onProjectIndexed("test_project");
    verify(forwarder, never())
        .index(any(IndexProjectTask.class), eq(new ProjectIndexEvent("test_project")));
  }

  @Test
  public void shouldNotForwardIndexChangeIfFilteredOutByProjectName() throws Exception {
    int changeId = 1;
    when(projectsFilter.matches(any(String.class))).thenReturn(false);

    eventHandler.onChangeIndexed("test_project", changeId);
    verifyZeroInteractions(changeChecker);
  }
}
