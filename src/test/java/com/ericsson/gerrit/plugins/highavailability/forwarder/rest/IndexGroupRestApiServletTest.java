// Copyright (C) 2017 Ericsson
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
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexGroupRestApiServletTest {
  private static final String UUID = "we235jdf92nfj2351";

  @Mock private GroupIndexer indexer;
  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse rsp;

  private AccountGroup.UUID uuid;
  private IndexGroupRestApiServlet servlet;

  @BeforeClass
  public static void setup() {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  @Before
  public void setUpMocks() {
    servlet = new IndexGroupRestApiServlet(indexer);
    uuid = AccountGroup.UUID.parse(UUID);
    when(req.getPathInfo()).thenReturn("/index/group/" + UUID);
  }

  @Test
  public void groupIsIndexed() throws Exception {
    servlet.doPost(req, rsp);
    verify(indexer, times(1)).index(uuid);
    verify(rsp).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void indexerThrowsIOExceptionTryingToIndexGroup() throws Exception {
    doThrow(new IOException("io-error")).when(indexer).index(uuid);
    servlet.doPost(req, rsp);
    verify(rsp).sendError(SC_CONFLICT, "io-error");
  }

  @Test
  public void sendErrorThrowsIOException() throws Exception {
    doThrow(new IOException("io-error")).when(indexer).index(uuid);
    doThrow(new IOException("someError")).when(rsp).sendError(SC_CONFLICT, "io-error");
    servlet.doPost(req, rsp);
    verify(rsp).sendError(SC_CONFLICT, "io-error");
  }
}
