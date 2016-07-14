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

package com.ericsson.gerrit.plugins.syncevents;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;

import com.google.common.net.MediaType;
import com.google.gerrit.common.EventDispatcher;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.EventTypes;
import com.google.gerrit.server.events.RefEvent;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StandardKeyEncoder;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SyncEventsRestApiServletTest extends EasyMockSupport {

  private EventDispatcher dispatcher = createStrictMock(EventDispatcher.class);
  private SyncEventsRestApiServlet syncEventsRestApiServlet;
  private HttpServletRequest req;
  private HttpServletResponse rsp;

  @BeforeClass
  public static void setup() {
    EventTypes.register(RefReplicationDoneEvent.TYPE, RefReplicationDoneEvent.class);
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  @Before
  public void createSyncEventsRestApiServlet() throws Exception {
    syncEventsRestApiServlet = new SyncEventsRestApiServlet(dispatcher);
    req = createNiceMock(HttpServletRequest.class);
    rsp = createNiceMock(HttpServletResponse.class);
    expect(req.getContentType()).andReturn(MediaType.JSON_UTF_8.toString());
  }

  @Test
  public void testDoPostRefReplicationDoneEvent() throws Exception {
    String event = "{\"project\":\"gerrit/test-sync-index\",\"ref\":"
        + "\"refs/changes/76/669676/2\",\"nodesCount\":1,\"type\":"
        + "\"ref-replication-done\",\"eventCreatedOn\":1451415011}";
    expect(req.getReader())
        .andReturn(new BufferedReader(new StringReader(event)));
    dispatcher.postEvent(isA(RefReplicationDoneEvent.class));
    rsp.setStatus(SC_NO_CONTENT);
    expectLastCall().once();
    replayAll();

    syncEventsRestApiServlet.doPost(req, rsp);
    verifyAll();
  }

  @Test
  public void testDoPostDispatcherFailure() throws Exception {
    String event = "{\"project\":\"gerrit/test-sync-index\",\"ref\":"
        + "\"refs/changes/76/669676/2\",\"nodesCount\":1,\"type\":"
        + "\"ref-replication-done\",\"eventCreatedOn\":1451415011}";
    expect(req.getReader())
        .andReturn(new BufferedReader(new StringReader(event)));
    dispatcher.postEvent(isA(RefReplicationDoneEvent.class));
    expectLastCall().andThrow(new OrmException("some Error"));
    rsp.sendError(SC_NOT_FOUND, "Change not found\n");
    expectLastCall().once();
    replayAll();

    syncEventsRestApiServlet.doPost(req, rsp);
    verifyAll();
  }

  @Test
  public void testDoPostBadRequest() throws Exception {
    expect(req.getReader()).andThrow(new IOException());
    replayAll();
    syncEventsRestApiServlet.doPost(req, rsp);
    verifyAll();
  }

  @Test
  public void testDoPostWrongMediaType() throws Exception {
    resetAll();
    expect(req.getContentType())
        .andReturn(MediaType.APPLICATION_XML_UTF_8.toString()).anyTimes();
    rsp.sendError(SC_UNSUPPORTED_MEDIA_TYPE,
        "Expecting " + JSON_UTF_8.toString() + " content type");
    expectLastCall().once();
    replayAll();
    syncEventsRestApiServlet.doPost(req, rsp);
    verifyAll();
  }

  @Test
  public void testDoPostErrorWhileSendingErrorMessage() throws Exception {
    expect(req.getReader()).andThrow(new IOException("someError"));
    rsp.sendError(SC_BAD_REQUEST, "someError");
    expectLastCall().andThrow(new IOException("someOtherError"));
    replayAll();
    syncEventsRestApiServlet.doPost(req, rsp);
    verifyAll();
  }

  static class RefReplicationDoneEvent extends RefEvent {
    public static final String TYPE = "ref-replication-done";
    public final String project;
    public final String ref;
    public final int nodesCount;

    public RefReplicationDoneEvent(String project, String ref, int nodesCount) {
      super(TYPE);
      this.project = project;
      this.ref = ref;
      this.nodesCount = nodesCount;
    }

    @Override
    public Project.NameKey getProjectNameKey() {
      return new Project.NameKey(project);
    }

    @Override
    public String getRefName() {
      return ref;
    }
  }
}
