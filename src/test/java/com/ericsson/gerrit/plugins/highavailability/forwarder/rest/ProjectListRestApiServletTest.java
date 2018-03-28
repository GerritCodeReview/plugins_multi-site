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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedProjectListUpdateHandler;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectListRestApiServletTest {
  private static final String PROJECT_NAME = "someProject";

  @Mock private ForwardedProjectListUpdateHandler handlerMock;
  @Mock private HttpServletRequest requestMock;
  @Mock private HttpServletResponse responseMock;

  private ProjectListApiServlet servlet;

  @Before
  public void setUpMocks() {
    servlet = new ProjectListApiServlet(handlerMock);
    when(requestMock.getPathInfo()).thenReturn("/cache/project_list/" + PROJECT_NAME);
  }

  @Test
  public void addProject() throws Exception {
    servlet.doPost(requestMock, responseMock);
    verify(handlerMock, times(1)).update(PROJECT_NAME, false);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void deleteProject() throws Exception {
    servlet.doDelete(requestMock, responseMock);
    verify(handlerMock, times(1)).update(PROJECT_NAME, true);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }
}
