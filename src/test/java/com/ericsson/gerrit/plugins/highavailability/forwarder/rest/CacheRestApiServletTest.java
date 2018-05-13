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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.ericsson.gerrit.plugins.highavailability.forwarder.CacheNotFoundException;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedCacheEvictionHandler;
import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CacheRestApiServletTest {
  @Mock private HttpServletRequest requestMock;
  @Mock private HttpServletResponse responseMock;
  @Mock private BufferedReader readerMock;
  @Mock private ForwardedCacheEvictionHandler forwardedCacheEvictionHandlerMock;
  private CacheRestApiServlet servlet;

  @Before
  public void setUp() {
    servlet = new CacheRestApiServlet(forwardedCacheEvictionHandlerMock);
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
    verifyResponseIsOK();
  }

  @Test
  public void evictPluginCache() throws Exception {
    configureMocksFor("my-plugin", "my-cache");
    verifyResponseIsOK();
  }

  @Test
  public void evictDefault() throws Exception {
    configureMocksFor(Constants.PROJECTS);
    verifyResponseIsOK();
  }

  @Test
  public void badRequest() throws Exception {
    when(requestMock.getPathInfo()).thenReturn("/someCache");
    String errorMessage = "someError";
    doThrow(new IOException(errorMessage)).when(requestMock).getReader();
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_BAD_REQUEST, errorMessage);
  }

  @Test
  public void badRequestCausedByCacheNotFound() throws Exception {
    String pluginName = "somePlugin";
    String cacheName = "nonexistingCache";
    configureMocksFor(pluginName, cacheName);
    CacheNotFoundException e = new CacheNotFoundException(pluginName, cacheName);
    doThrow(e).when(forwardedCacheEvictionHandlerMock).evict(any());
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_BAD_REQUEST, e.getMessage());
  }

  private void verifyResponseIsOK() throws Exception {
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  private void configureMocksFor(String cacheName) throws Exception {
    configureMocksFor(Constants.GERRIT, cacheName);
  }

  private void configureMocksFor(String pluginName, String cacheName) throws Exception {
    if (Constants.GERRIT.equals(pluginName)) {
      when(requestMock.getPathInfo()).thenReturn("/" + cacheName);
    } else {
      when(requestMock.getPathInfo()).thenReturn("/" + pluginName + "." + cacheName);
    }
    when(requestMock.getReader()).thenReturn(readerMock);

    if (Constants.PROJECTS.equals(cacheName)) {
      when(readerMock.readLine()).thenReturn("abc");
    } else if (Constants.GROUPS_BYINCLUDE.equals(cacheName)
        || Constants.GROUPS_MEMBERS.equals(cacheName)) {
      when(readerMock.readLine()).thenReturn("{\"uuid\":\"abcd1234\"}");
    } else {
      when(readerMock.readLine()).thenReturn("{}");
    }
  }
}
