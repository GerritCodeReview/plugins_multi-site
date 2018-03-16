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
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.account.AccountIndexer;
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
public class IndexAccountRestApiServletTest {
  private static final String ACCOUNT_NUMBER = "1";

  @Mock private AccountIndexer indexerMock;
  @Mock private HttpServletRequest requestMock;
  @Mock private HttpServletResponse responseMock;

  private Account.Id id;
  private IndexAccountRestApiServlet servlet;

  @BeforeClass
  public static void setup() {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  @Before
  public void setUpMocks() {
    servlet = new IndexAccountRestApiServlet(indexerMock);
    id = Account.Id.parse(ACCOUNT_NUMBER);
    when(requestMock.getPathInfo()).thenReturn("/index/account/" + ACCOUNT_NUMBER);
  }

  @Test
  public void accountIsIndexed() throws Exception {
    servlet.doPost(requestMock, responseMock);
    verify(indexerMock, times(1)).index(id);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void cannotDeleteAccount() throws Exception {
    servlet.doDelete(requestMock, responseMock);
    verify(responseMock).sendError(SC_METHOD_NOT_ALLOWED, "cannot delete account from index");
  }

  @Test
  public void indexerThrowsIOExceptionTryingToIndexAccount() throws Exception {
    doThrow(new IOException("io-error")).when(indexerMock).index(id);
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, "io-error");
  }

  @Test
  public void sendErrorThrowsIOException() throws Exception {
    doThrow(new IOException("io-error")).when(indexerMock).index(id);
    doThrow(new IOException("someError")).when(responseMock).sendError(SC_CONFLICT, "io-error");
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, "io-error");
  }
}
