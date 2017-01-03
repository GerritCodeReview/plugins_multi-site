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

package com.ericsson.gerrit.plugins.syncindex;

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
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.gwtorm.server.StandardKeyEncoder;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class SyncIndexRestApiServletTest {
  private static final boolean CHANGE_EXISTS = true;
  private static final boolean CHANGE_DOES_NOT_EXIST = false;
  private static final boolean DO_NOT_THROW_IO_EXCEPTION = false;
  private static final boolean DO_NOT_THROW_ORM_EXCEPTION = false;
  private static final boolean THROW_IO_EXCEPTION = true;
  private static final boolean THROW_ORM_EXCEPTION = true;
  private static final String CHANGE_NUMBER = "1";

  @Mock
  private ChangeIndexer indexer;
  @Mock
  private SchemaFactory<ReviewDb> schemaFactory;
  @Mock
  private ReviewDb db;
  @Mock
  private HttpServletRequest req;
  @Mock
  private HttpServletResponse rsp;
  private Change.Id id;
  private Change change;
  private SyncIndexRestApiServlet syncIndexRestApiServlet;

  @BeforeClass
  public static void setup() {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  @Before
  public void setUpMocks() {
    syncIndexRestApiServlet =
        new SyncIndexRestApiServlet(indexer, schemaFactory);
    id = Change.Id.parse(CHANGE_NUMBER);
    when(req.getPathInfo()).thenReturn("/index/" + CHANGE_NUMBER);
    change = new Change(null, id, null, null, TimeUtil.nowTs());
  }

  @Test
  public void changeIsIndexed() throws Exception {
    setupPostMocks(CHANGE_EXISTS);
    syncIndexRestApiServlet.doPost(req, rsp);
    verify(indexer, times(1)).index(db, change);
    verify(rsp).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void changeToIndexDoNotExist() throws Exception {
    setupPostMocks(CHANGE_DOES_NOT_EXIST);
    syncIndexRestApiServlet.doPost(req, rsp);
    verify(indexer, times(1)).delete(id);
    verify(rsp).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void schemaThrowsExceptionWhenLookingUpForChange() throws Exception {
    setupPostMocks(CHANGE_EXISTS, THROW_ORM_EXCEPTION);
    syncIndexRestApiServlet.doPost(req, rsp);
    verify(rsp).sendError(SC_NOT_FOUND, "Error trying to find a change \n");
  }

  @Test
  public void indexerThrowsIOExceptionTryingToIndexChange() throws Exception {
    setupPostMocks(CHANGE_EXISTS, DO_NOT_THROW_ORM_EXCEPTION,
        THROW_IO_EXCEPTION);
    syncIndexRestApiServlet.doPost(req, rsp);
    verify(rsp).sendError(SC_CONFLICT, "io-error");
  }

  @Test
  public void changeIsDeletedFromIndex() throws Exception {
    syncIndexRestApiServlet.doDelete(req, rsp);
    verify(indexer, times(1)).delete(id);
    verify(rsp).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void indexerThrowsExceptionTryingToDeleteChange() throws Exception {
    doThrow(new IOException("io-error")).when(indexer).delete(id);
    syncIndexRestApiServlet.doDelete(req, rsp);
    verify(rsp).sendError(SC_CONFLICT, "io-error");
  }

  @Test
  public void sendErrorThrowsIOException() throws Exception {
    doThrow(new IOException("someError")).when(rsp).sendError(SC_NOT_FOUND,
        "Error trying to find a change \n");
    setupPostMocks(CHANGE_EXISTS, THROW_ORM_EXCEPTION);
    syncIndexRestApiServlet.doPost(req, rsp);
    verify(rsp).sendError(SC_NOT_FOUND, "Error trying to find a change \n");
    verifyZeroInteractions(indexer);
  }

  private void setupPostMocks(boolean changeExist) throws Exception {
    setupPostMocks(changeExist, DO_NOT_THROW_ORM_EXCEPTION,
        DO_NOT_THROW_IO_EXCEPTION);
  }

  private void setupPostMocks(boolean changeExist, boolean ormException)
      throws OrmException, IOException {
    setupPostMocks(changeExist, ormException, DO_NOT_THROW_IO_EXCEPTION);
  }

  private void setupPostMocks(boolean changeExist, boolean ormException,
      boolean ioException) throws OrmException, IOException {
    if (ormException) {
      doThrow(new OrmException("")).when(schemaFactory).open();
    } else {
      when(schemaFactory.open()).thenReturn(db);
      ChangeAccess ca = mock(ChangeAccess.class);
      when(db.changes()).thenReturn(ca);
      if (changeExist) {
        when(ca.get(id)).thenReturn(change);
        if (ioException) {
          doThrow(new IOException("io-error")).when(indexer).index(db, change);
        }
      } else {
        when(ca.get(id)).thenReturn(null);
      }
    }
  }
}
