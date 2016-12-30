// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.evictcache;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class EvictCacheRestApiServletTest {
  @Mock
  private HttpServletRequest request;
  @Mock
  private HttpServletResponse response;
  @Mock
  private BufferedReader reader;
  @Mock
  private DynamicMap<Cache<?, ?>> cacheMap;
  private EvictCacheRestApiServlet servlet;

  @Before
  public void setUp() {
    servlet = new EvictCacheRestApiServlet(cacheMap);
  }

  @Test
  public void evictAccounts() throws Exception {
    configureMocksFor(Constants.ACCOUNTS);
    verifyResponseIsOK();
  }

  @Test
  public void evictProjectList() throws Exception {
    configureMocksFor(Constants.PROJECT_LIST);
    verifyResponseIsOK();
  }

  @Test
  public void evictGroups() throws Exception {
    configureMocksFor(Constants.GROUPS);
    verifyResponseIsOK();
  }

  @Test
  public void evictGroupsByInclude() throws Exception {
    configureMocksFor(Constants.GROUPS_BYINCLUDE);
    verifyResponseIsOK();
  }

  @Test
  public void evictGroupsMembers() throws Exception {
    configureMocksFor(Constants.GROUPS_MEMBERS);
    servlet.doPost(request, response);

  }

  @Test
  public void evictDefault() throws Exception {
    configureMocksFor(Constants.DEFAULT);
    verifyResponseIsOK();
  }

  private void verifyResponseIsOK() throws Exception {
    servlet.doPost(request, response);
    verify(response).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void badRequest() throws Exception {
    when(request.getPathInfo()).thenReturn("/someCache");
    String errorMessage = "someError";
    doThrow(new IOException(errorMessage)).when(request).getReader();
    servlet.doPost(request, response);
    verify(response).sendError(SC_BAD_REQUEST, errorMessage);
  }

  @Test
  public void errorWhileSendingErrorMessage() throws Exception {
    when(request.getPathInfo()).thenReturn("/someCache");
    String errorMessage = "someError";
    doThrow(new IOException(errorMessage)).when(request).getReader();
    servlet.doPost(request, response);
    verify(response).sendError(SC_BAD_REQUEST, errorMessage);
  }

  @SuppressWarnings("unchecked")
  private void configureMocksFor(String cacheName) throws IOException {
    when(cacheMap.get("gerrit", cacheName)).thenReturn(mock(Cache.class));
    when(request.getPathInfo()).thenReturn("/" + cacheName);
    when(request.getReader()).thenReturn(reader);

    if (Constants.DEFAULT.equals(cacheName)) {
      when(reader.readLine()).thenReturn("abc");
    } else if (Constants.GROUPS_BYINCLUDE.equals(cacheName)
        || Constants.GROUPS_MEMBERS.equals(cacheName)) {
      when(reader.readLine()).thenReturn("{\"uuid\":\"abcd1234\"}");
    } else {
      when(reader.readLine()).thenReturn("{}");
    }
  }
}
