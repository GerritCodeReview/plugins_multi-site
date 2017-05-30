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

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class IndexGroupRestApiServlet extends HttpServlet {
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory.getLogger(IndexGroupRestApiServlet.class);
  private static final Map<AccountGroup.UUID, AtomicInteger> accountGroupIdLocks = new HashMap<>();

  private final GroupIndexer indexer;

  @Inject
  IndexGroupRestApiServlet(GroupIndexer indexer) {
    this.indexer = indexer;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding(UTF_8.name());
    String path = req.getPathInfo();
    String accountGroupId = path.substring(path.lastIndexOf('/') + 1);
    AccountGroup.UUID uuid = AccountGroup.UUID.parse(accountGroupId);
    try {
      Context.setForwardedEvent(true);
      index(uuid);
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      sendError(rsp, SC_CONFLICT, e.getMessage());
      logger.error("Unable to update account index", e);
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private static void sendError(HttpServletResponse rsp, int statusCode, String message) {
    try {
      rsp.sendError(statusCode, message);
    } catch (IOException e) {
      logger.error("Failed to send error messsage: " + e.getMessage(), e);
    }
  }

  private void index(AccountGroup.UUID uuid) throws IOException {
    AtomicInteger accountGroupIdLock = getAndIncrementAccountGroupIdLock(uuid);
    synchronized (accountGroupIdLock) {
      indexer.index(uuid);
      logger.debug("Group {} successfully indexed", uuid);
    }
    if (accountGroupIdLock.decrementAndGet() == 0) {
      removeAccountGroupIdLock(uuid);
    }
  }

  private AtomicInteger getAndIncrementAccountGroupIdLock(AccountGroup.UUID uuid) {
    synchronized (accountGroupIdLocks) {
      AtomicInteger accountGroupIdLock = accountGroupIdLocks.get(uuid);
      if (accountGroupIdLock == null) {
        accountGroupIdLock = new AtomicInteger(1);
        accountGroupIdLocks.put(uuid, accountGroupIdLock);
      } else {
        accountGroupIdLock.incrementAndGet();
      }
      return accountGroupIdLock;
    }
  }

  private void removeAccountGroupIdLock(AccountGroup.UUID uuid) {
    synchronized (accountGroupIdLocks) {
      accountGroupIdLocks.remove(uuid);
    }
  }
}
