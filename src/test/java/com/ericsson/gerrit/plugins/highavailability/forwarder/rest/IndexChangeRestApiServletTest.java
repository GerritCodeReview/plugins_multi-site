// Copyright (C) 2016 Ericsson
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
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
  private static final boolean CHANGE_EXISTS = true;
  private static final boolean CHANGE_DOES_NOT_EXIST = false;
  private static final boolean DO_NOT_THROW_IO_EXCEPTION = false;
  private static final boolean DO_NOT_THROW_ORM_EXCEPTION = false;
  private static final boolean THROW_IO_EXCEPTION = true;
  private static final boolean THROW_ORM_EXCEPTION = true;
  private static final int CHANGE_NUMBER = 1;

  @Mock private ChangeIndexer indexerMock;
  @Mock private SchemaFactory<ReviewDb> schemaFactoryMock;
  @Mock private ReviewDb dbMock;
  @Mock private HttpServletRequest requestMock;
  @Mock private HttpServletResponse responseMock;
  private Change.Id id;
  private Change change;
  private IndexChangeRestApiServlet indexRestApiServlet;

  @Before
  public void setUpMocks() {
    indexRestApiServlet = new IndexChangeRestApiServlet(indexerMock, schemaFactoryMock);
    id = new Change.Id(CHANGE_NUMBER);
    when(requestMock.getPathInfo()).thenReturn("/index/change/" + CHANGE_NUMBER);
    change = new Change(null, id, null, null, TimeUtil.nowTs());
  }

  @Test
  public void changeIsIndexed() throws Exception {
    setupPostMocks(CHANGE_EXISTS);
    indexRestApiServlet.doPost(requestMock, responseMock);
    verify(indexerMock, times(1)).index(dbMock, change);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void changeToIndexDoNotExist() throws Exception {
    setupPostMocks(CHANGE_DOES_NOT_EXIST);
    indexRestApiServlet.doPost(requestMock, responseMock);
    verify(indexerMock, times(1)).delete(id);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void schemaThrowsExceptionWhenLookingUpForChange() throws Exception {
    setupPostMocks(CHANGE_EXISTS, THROW_ORM_EXCEPTION);
    indexRestApiServlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_NOT_FOUND, "Error trying to find change \n");
  }

  @Test
  public void indexerThrowsNoSuchChangeExceptionTryingToPostChange() throws Exception {
    doThrow(new NoSuchChangeException(id)).when(schemaFactoryMock).open();
    indexRestApiServlet.doPost(requestMock, responseMock);
    verify(indexerMock, times(1)).delete(id);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void indexerThrowsNestedNoSuchChangeExceptionTryingToPostChange() throws Exception {
    OrmException e = new OrmException("test", new NoSuchChangeException(id));
    doThrow(e).when(schemaFactoryMock).open();
    indexRestApiServlet.doPost(requestMock, responseMock);
    verify(indexerMock, times(1)).delete(id);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void indexerThrowsIOExceptionTryingToIndexChange() throws Exception {
    setupPostMocks(CHANGE_EXISTS, DO_NOT_THROW_ORM_EXCEPTION, THROW_IO_EXCEPTION);
    indexRestApiServlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, "io-error");
  }

  @Test
  public void changeIsDeletedFromIndex() throws Exception {
    indexRestApiServlet.doDelete(requestMock, responseMock);
    verify(indexerMock, times(1)).delete(id);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void indexerThrowsExceptionTryingToDeleteChange() throws Exception {
    doThrow(new IOException("io-error")).when(indexerMock).delete(id);
    indexRestApiServlet.doDelete(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, "io-error");
  }

  @Test
  public void sendErrorThrowsIOException() throws Exception {
    doThrow(new IOException("someError"))
        .when(responseMock)
        .sendError(SC_NOT_FOUND, "Error trying to find change \n");
    setupPostMocks(CHANGE_EXISTS, THROW_ORM_EXCEPTION);
    indexRestApiServlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_NOT_FOUND, "Error trying to find change \n");
    verifyZeroInteractions(indexerMock);
  }

  private void setupPostMocks(boolean changeExist) throws Exception {
    setupPostMocks(changeExist, DO_NOT_THROW_ORM_EXCEPTION, DO_NOT_THROW_IO_EXCEPTION);
  }

  private void setupPostMocks(boolean changeExist, boolean ormException)
      throws OrmException, IOException {
    setupPostMocks(changeExist, ormException, DO_NOT_THROW_IO_EXCEPTION);
  }

  private void setupPostMocks(boolean changeExist, boolean ormException, boolean ioException)
      throws OrmException, IOException {
    if (ormException) {
      doThrow(new OrmException("")).when(schemaFactoryMock).open();
    } else {
      when(schemaFactoryMock.open()).thenReturn(dbMock);
      ChangeAccess ca = mock(ChangeAccess.class);
      when(dbMock.changes()).thenReturn(ca);
      if (changeExist) {
        when(ca.get(id)).thenReturn(change);
        if (ioException) {
          doThrow(new IOException("io-error")).when(indexerMock).index(dbMock, change);
        }
      } else {
        when(ca.get(id)).thenReturn(null);
      }
    }
  }
}
