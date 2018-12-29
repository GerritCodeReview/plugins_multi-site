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

package com.googlesource.gerrit.plugins.multisite.forwarder.rest;

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Project;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexProjectHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexProjectRestApiServletTest {
  private static final String IO_ERROR = "io-error";
  private static final String PROJECT_NAME = "test/project";

  @Mock private ForwardedIndexProjectHandler handlerMock;
  @Mock private HttpServletRequest requestMock;
  @Mock private HttpServletResponse responseMock;

  private Project.NameKey nameKey;
  private IndexProjectRestApiServlet servlet;

  @Before
  public void setUpMocks() {
    servlet = new IndexProjectRestApiServlet(handlerMock);
    nameKey = new Project.NameKey(PROJECT_NAME);
    when(requestMock.getRequestURI())
        .thenReturn("http://gerrit.com/index/project/" + Url.encode(nameKey.get()));
  }

  @Test
  public void projectIsIndexed() throws Exception {
    servlet.doPost(requestMock, responseMock);
    verify(handlerMock, times(1)).index(eq(nameKey), eq(Operation.INDEX), any());
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void cannotDeleteProject() throws Exception {
    servlet.doDelete(requestMock, responseMock);
    verify(responseMock).sendError(SC_METHOD_NOT_ALLOWED, "cannot delete project from index");
  }

  @Test
  public void indexerThrowsIOExceptionTryingToIndexProject() throws Exception {
    doThrow(new IOException(IO_ERROR))
        .when(handlerMock)
        .index(eq(nameKey), eq(Operation.INDEX), any());
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, IO_ERROR);
  }

  @Test
  public void sendErrorThrowsIOException() throws Exception {
    doThrow(new IOException(IO_ERROR))
        .when(handlerMock)
        .index(eq(nameKey), eq(Operation.INDEX), any());
    doThrow(new IOException("someError")).when(responseMock).sendError(SC_CONFLICT, IO_ERROR);
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, IO_ERROR);
  }
}
