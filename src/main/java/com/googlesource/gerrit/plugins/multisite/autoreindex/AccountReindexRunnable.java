// Copyright (C) 2018 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.autoreindex;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexAccountHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation;
import com.googlesource.gerrit.plugins.multisite.forwarder.rest.AbstractIndexRestApiServlet;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountReindexRunnable extends ReindexRunnable<AccountState> {
  private static final Logger log = LoggerFactory.getLogger(AccountReindexRunnable.class);

  private final ForwardedIndexAccountHandler accountIdx;

  private final Accounts accounts;

  @Inject
  public AccountReindexRunnable(
      ForwardedIndexAccountHandler accountIdx,
      IndexTs indexTs,
      OneOffRequestContext ctx,
      Accounts accounts) {
    super(AbstractIndexRestApiServlet.IndexName.ACCOUNT, indexTs, ctx);
    this.accountIdx = accountIdx;
    this.accounts = accounts;
  }

  @Override
  protected Iterable<AccountState> fetchItems() throws Exception {
    return accounts.all();
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(AccountState as, Timestamp sinceTs) {
    try {
      Account a = as.getAccount();
      Timestamp accountTs = a.getRegisteredOn();
      if (accountTs.after(sinceTs)) {
        log.info("Index {}/{}/{}/{}", a.getId(), a.getFullName(), a.getPreferredEmail(), accountTs);
        accountIdx.index(a.getId(), Operation.INDEX, Optional.empty());
        return Optional.of(accountTs);
      }
    } catch (IOException | OrmException e) {
      log.error("Reindex failed", e);
    }
    return Optional.empty();
  }
}
