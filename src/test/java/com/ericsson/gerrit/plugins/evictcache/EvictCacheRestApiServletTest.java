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
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EvictCacheRestApiServletTest extends EasyMockSupport {
  private static final String PLUGIN_NAME = "gerrit";
  private HttpServletRequest request;
  private HttpServletResponse response;
  private BufferedReader reader;
  private EvictCacheRestApiServlet servlet;
  private DynamicMap<Cache<?, ?>> cacheMap;
  @SuppressWarnings("rawtypes")
  private Cache cache;

  @Test
  public void evictAccounts() throws IOException, ServletException {
    configureMocksFor(Constants.ACCOUNTS);
    servlet.doPost(request, response);
    verifyAll();
  }

  @Test
  public void evictProjectList() throws IOException, ServletException {
    configureMocksFor(Constants.PROJECT_LIST);
    servlet.doPost(request, response);
    verifyAll();
  }

  @Test
  public void evictGroups() throws IOException, ServletException {
    configureMocksFor(Constants.GROUPS);
    servlet.doPost(request, response);
    verifyAll();
  }

  @Test
  public void evictGroupsByInclude() throws IOException, ServletException {
    configureMocksFor(Constants.GROUPS_BYINCLUDE);
    servlet.doPost(request, response);
    verifyAll();
  }

  @Test
  public void evictGroupsMembers() throws IOException, ServletException {
    configureMocksFor(Constants.GROUPS_MEMBERS);
    servlet.doPost(request, response);
    verifyAll();
  }

  @Test
  public void evictDefault() throws IOException, ServletException {
    configureMocksFor(Constants.DEFAULT);
    servlet.doPost(request, response);
    verifyAll();
  }

  @Test
  public void badRequest() throws IOException, ServletException {
    expect(request.getPathInfo()).andReturn("/someCache");
    String errorMessage = "someError";
    expect(request.getReader()).andThrow(new IOException(errorMessage));
    response.sendError(SC_BAD_REQUEST, errorMessage);
    expectLastCall().once();
    replayAll();
    servlet.doPost(request, response);
    verifyAll();
  }

  @Test
  public void errorWhileSendingErrorMessage() throws Exception {
    expect(request.getPathInfo()).andReturn("/someCache");
    String errorMessage = "someError";
    expect(request.getReader()).andThrow(new IOException(errorMessage));
    response.sendError(SC_BAD_REQUEST, errorMessage);
    expectLastCall().andThrow(new IOException("someOtherError"));
    replayAll();
    servlet.doPost(request, response);
    verifyAll();
  }

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    cacheMap = createMock(DynamicMap.class);
    request = createMock(HttpServletRequest.class);
    reader = createMock(BufferedReader.class);
    response = createNiceMock(HttpServletResponse.class);
    cache = createNiceMock(Cache.class);
    servlet = new EvictCacheRestApiServlet(cacheMap);
  }

  @SuppressWarnings("unchecked")
  private void configureMocksFor(String cacheName) throws IOException {
    expect(cacheMap.get(PLUGIN_NAME, cacheName)).andReturn(cache);
    expect(request.getPathInfo()).andReturn("/" + cacheName);
    expect(request.getReader()).andReturn(reader);

    if (Constants.DEFAULT.equals(cacheName)) {
      expect(reader.readLine()).andReturn("abc");
    } else if (Constants.GROUPS_BYINCLUDE.equals(cacheName)
        || Constants.GROUPS_MEMBERS.equals(cacheName)) {
      expect(reader.readLine()).andReturn("{\"uuid\":\"abcd1234\"}");
    } else {
      expect(reader.readLine()).andReturn("{}");
    }
    response.setStatus(SC_NO_CONTENT);
    expectLastCall().once();
    replayAll();
  }
}
