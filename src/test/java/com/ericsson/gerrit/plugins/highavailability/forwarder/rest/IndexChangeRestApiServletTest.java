// Copyright (C) 2016 The Android Open Source Project
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

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexChangeRestApiServletTest {
  private static final int CHANGE_NUMBER = 1;
  private static final String PROJECT_NAME = "test/project";
  private static final String PROJECT_NAME_URL_ENC = "test%2Fproject";
  private static final String CHANGE_ID = PROJECT_NAME + "~" + CHANGE_NUMBER;
  private static final String IO_ERROR = "io-error";

  @Mock private ForwardedIndexChangeHandler handlerMock;
  @Mock private HttpServletRequest requestMock;
  @Mock private HttpServletResponse responseMock;

  private IndexChangeRestApiServlet servlet;

  @Before
  public void setUpMocks() {
    servlet = new IndexChangeRestApiServlet(handlerMock);
    when(requestMock.getRequestURI())
        .thenReturn("http://gerrit.com/index/change/" + PROJECT_NAME_URL_ENC + "~" + CHANGE_NUMBER);
  }

  @Test
  public void changeIsIndexed() throws Exception {
    servlet.doPost(requestMock, responseMock);
    verify(handlerMock, times(1)).index(eq(CHANGE_ID), eq(Operation.INDEX), any());
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void changeIsDeletedFromIndex() throws Exception {
    servlet.doDelete(requestMock, responseMock);
    verify(handlerMock, times(1)).index(eq(CHANGE_ID), eq(Operation.DELETE), any());
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void indexerThrowsIOExceptionTryingToIndexChange() throws Exception {
    doThrow(new IOException(IO_ERROR))
        .when(handlerMock)
        .index(eq(CHANGE_ID), eq(Operation.INDEX), any());
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, IO_ERROR);
  }

  @Test
  public void indexerThrowsOrmExceptionTryingToIndexChange() throws Exception {
    doThrow(new OrmException("some message"))
        .when(handlerMock)
        .index(eq(CHANGE_ID), eq(Operation.INDEX), any());
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_NOT_FOUND, "Error trying to find change");
  }

  @Test
  public void sendErrorThrowsIOException() throws Exception {
    doThrow(new IOException(IO_ERROR))
        .when(handlerMock)
        .index(eq(CHANGE_ID), eq(Operation.INDEX), any());
    doThrow(new IOException("someError")).when(responseMock).sendError(SC_CONFLICT, IO_ERROR);
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, IO_ERROR);
  }
}
