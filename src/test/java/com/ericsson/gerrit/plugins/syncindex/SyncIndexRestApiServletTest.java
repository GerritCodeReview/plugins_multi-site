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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.gwtorm.server.StandardKeyEncoder;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SyncIndexRestApiServletTest extends EasyMockSupport {
  private static final boolean CHANGE_EXISTS = true;
  private static final boolean CHANGE_DOES_NOT_EXIST = false;
  private static final boolean DO_NOT_THROW_IO_EXCEPTION = false;
  private static final boolean DO_NOT_THROW_ORM_EXCEPTION = false;
  private static final boolean THROW_IO_EXCEPTION = true;
  private static final boolean THROW_ORM_EXCEPTION = true;
  private static final String CHANGE_NUMBER = "1";

  private ChangeIndexer indexer;
  private SchemaFactory<ReviewDb> schemaFactory;
  private SyncIndexRestApiServlet syncIndexRestApiServlet;
  private HttpServletRequest req;
  private HttpServletResponse rsp;
  private Change.Id id;

  @BeforeClass
  public static void setup() {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  @Before
  @SuppressWarnings("unchecked")
  public void setUpMocks() {
    indexer = createNiceMock(ChangeIndexer.class);
    schemaFactory = createNiceMock(SchemaFactory.class);
    req = createNiceMock(HttpServletRequest.class);
    rsp = createNiceMock(HttpServletResponse.class);
    syncIndexRestApiServlet =
        new SyncIndexRestApiServlet(indexer, schemaFactory);
    id = Change.Id.parse(CHANGE_NUMBER);

    expect(req.getPathInfo()).andReturn("/index/" + CHANGE_NUMBER);
  }

  @Test
  public void changeIsIndexed() throws Exception {
    setupPostMocks(CHANGE_EXISTS);
    verifyPost();
  }

  @Test
  public void changeToIndexDoNotExist() throws Exception {
    setupPostMocks(CHANGE_DOES_NOT_EXIST);
    verifyPost();
  }

  @Test
  public void schemaThrowsExceptionWhenLookingUpForChange() throws Exception {
    setupPostMocks(CHANGE_EXISTS, THROW_ORM_EXCEPTION);
    verifyPost();
  }

  @Test
  public void indexerThrowsIOExceptionTryingToIndexChange() throws Exception {
    setupPostMocks(CHANGE_EXISTS, DO_NOT_THROW_ORM_EXCEPTION,
        THROW_IO_EXCEPTION);
    verifyPost();
  }

  @Test
  public void changeIsDeletedFromIndex() throws Exception {
    setupDeleteMocks(DO_NOT_THROW_IO_EXCEPTION);
    verifyDelete();
  }

  @Test
  public void indexerThrowsExceptionTryingToDeleteChange() throws Exception {
    setupDeleteMocks(THROW_IO_EXCEPTION);
    verifyDelete();
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
      expect(schemaFactory.open()).andThrow(new OrmException(""));
    } else {
      ReviewDb db = createNiceMock(ReviewDb.class);
      expect(schemaFactory.open()).andReturn(db);
      ChangeAccess ca = createNiceMock(ChangeAccess.class);
      expect(db.changes()).andReturn(ca);

      if (changeExist) {
        Change change = new Change(null, id, null, null, TimeUtil.nowTs());
        expect(ca.get(id)).andReturn(change);
        indexer.index(db, change);
        if (ioException) {
          expectLastCall().andThrow(new IOException());
        } else {
          expectLastCall().once();
        }
      } else {
        expect(ca.get(id)).andReturn(null);
      }
    }
    replayAll();
  }

  private void verifyPost() throws IOException, ServletException {
    syncIndexRestApiServlet.doPost(req, rsp);
    verifyAll();
  }

  private void setupDeleteMocks(boolean exception) throws IOException {
    indexer.delete(id);
    if (exception) {
      expectLastCall().andThrow(new IOException());
    } else {
      expectLastCall().once();
    }
    replayAll();
  }

  private void verifyDelete() throws IOException, ServletException {
    syncIndexRestApiServlet.doDelete(req, rsp);
    verifyAll();
  }
}
