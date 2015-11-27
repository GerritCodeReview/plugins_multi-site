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

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.expect;

import com.google.common.base.Joiner;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;

import com.ericsson.gerrit.plugins.evictcache.CacheResponseHandler.CacheResult;

import org.easymock.EasyMockSupport;
import org.junit.Test;

import java.io.IOException;

public class RestSessionTest extends EasyMockSupport {
  private static final String EVICT = "evict";
  private static final String SOURCE_NAME = "gerrit";
  private static final String PLUGIN_NAME = "evict-cache";
  private static final String EMPTY_JSON = "{}";
  private static final String EMPTY_JSON2 = "\"{}\"";
  private static final String ID_RESPONSE = "{\"id\":0}";

  private RestSession restClient;

  @Test
  public void testEvictCacheOK() throws Exception {
    setupMocks(Constants.DEFAULT, EMPTY_JSON2, true, false);
    assertThat(restClient.evict(SOURCE_NAME, Constants.DEFAULT, EMPTY_JSON))
        .isTrue();
  }

  @Test
  public void testEvictAccountsOK() throws Exception {
    setupMocks(Constants.ACCOUNTS, ID_RESPONSE, true, false);
    assertThat(restClient.evict(SOURCE_NAME, Constants.ACCOUNTS,
        createMock(Account.Id.class))).isTrue();
  }

  @Test
  public void testEvictGroupsOK() throws Exception {
    setupMocks(Constants.GROUPS, ID_RESPONSE, true, false);
    assertThat(restClient.evict(SOURCE_NAME, Constants.GROUPS,
        createMock(AccountGroup.Id.class))).isTrue();
  }

  @Test
  public void testEvictGroupsByIncludeOK() throws Exception {
    setupMocks(Constants.GROUPS_BYINCLUDE, EMPTY_JSON, true, false);
    assertThat(restClient.evict(SOURCE_NAME, Constants.GROUPS_BYINCLUDE,
        createMock(AccountGroup.UUID.class))).isTrue();
  }

  @Test
  public void testEvictGroupsMembersOK() throws Exception {
    setupMocks(Constants.GROUPS_MEMBERS, EMPTY_JSON, true, false);
    assertThat(restClient.evict(SOURCE_NAME, Constants.GROUPS_MEMBERS,
        createMock(AccountGroup.UUID.class))).isTrue();
  }

  @Test
  public void testEvictProjectListOK() throws Exception {
    setupMocks(Constants.PROJECT_LIST, EMPTY_JSON, true, false);
    assertThat(
        restClient.evict(SOURCE_NAME, Constants.PROJECT_LIST, new Object()))
            .isTrue();
  }

  @Test
  public void testEvictCacheFailed() throws Exception {
    setupMocks(Constants.DEFAULT, EMPTY_JSON2, false, false);
    assertThat(restClient.evict(SOURCE_NAME, Constants.DEFAULT, EMPTY_JSON))
        .isFalse();
  }

  @Test
  public void testEvictCacheThrowsException() throws Exception {
    setupMocks(Constants.DEFAULT, EMPTY_JSON2, false, true);
    assertThat(restClient.evict(SOURCE_NAME, Constants.DEFAULT, EMPTY_JSON))
        .isFalse();
  }

  private void setupMocks(String cacheName, String json, boolean ok,
      boolean exception) throws IOException {
    String request = Joiner.on("/").join("/plugins", PLUGIN_NAME, SOURCE_NAME,
        EVICT, cacheName);
    HttpSession httpSession = createNiceMock(HttpSession.class);
    if (exception) {
      expect(httpSession.post(request, json)).andThrow(new IOException());
    } else {
      CacheResult result = new CacheResult(ok, "Error");
      expect(httpSession.post(request, json)).andReturn(result);
    }
    replayAll();
    restClient = new RestSession(httpSession, PLUGIN_NAME);
  }
}
