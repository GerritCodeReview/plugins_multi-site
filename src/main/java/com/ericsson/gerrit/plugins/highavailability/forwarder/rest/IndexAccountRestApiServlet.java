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

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.account.AccountIndexer;
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
class IndexAccountRestApiServlet extends HttpServlet {
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory.getLogger(IndexAccountRestApiServlet.class);
  private static final Map<Account.Id, AtomicInteger> accountIdLocks = new HashMap<>();

  private final AccountIndexer indexer;

  @Inject
  IndexAccountRestApiServlet(AccountIndexer indexer) {
    this.indexer = indexer;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding("UTF-8");
    String path = req.getPathInfo();
    String accountId = path.substring(path.lastIndexOf('/') + 1);
    Account.Id id = Account.Id.parse(accountId);
    try {
      Context.setForwardedEvent(true);
      index(id);
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

  private void index(Account.Id id) throws IOException {
    AtomicInteger accountIdLock = getAndIncrementAccountIdLock(id);
    synchronized (accountIdLock) {
      indexer.index(id);
      logger.debug("Account {} successfully indexed", id);
    }
    if (accountIdLock.decrementAndGet() == 0) {
      removeAccountIdLock(id);
    }
  }

  private AtomicInteger getAndIncrementAccountIdLock(Account.Id id) {
    synchronized (accountIdLocks) {
      AtomicInteger accountIdLock = accountIdLocks.get(id);
      if (accountIdLock == null) {
        accountIdLock = new AtomicInteger(1);
        accountIdLocks.put(id, accountIdLock);
      } else {
        accountIdLock.incrementAndGet();
      }
      return accountIdLock;
    }
  }

  private void removeAccountIdLock(Account.Id id) {
    synchronized (accountIdLocks) {
      accountIdLocks.remove(id);
    }
  }
}
