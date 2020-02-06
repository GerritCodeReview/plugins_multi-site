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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Index an account using {@link AccountIndexer}. This class is meant to be used on the receiving
 * side of the {@link IndexEventForwarder} since it will prevent indexed account to be forwarded
 * again causing an infinite forwarding loop between the 2 nodes. It will also make sure no
 * concurrent indexing is done for the same account id
 */
@Singleton
public class ForwardedIndexAccountHandler
    extends ForwardedIndexingHandler<Account.Id, AccountIndexEvent> {

  private final AccountIndexer indexer;
  private Map<Account.Id, Operation> accountsToIndex;

  @Inject
  ForwardedIndexAccountHandler(AccountIndexer indexer, Configuration config) {
    super(config.index().numStripedLocks());
    this.indexer = indexer;
    this.accountsToIndex = new HashMap<>();
  }

  @Override
  protected void doIndex(Account.Id id, Optional<AccountIndexEvent> event) {
    indexer.index(id);
    log.debug("Account {} successfully indexed", id);
  }

  @Override
  protected void doDelete(Account.Id id, Optional<AccountIndexEvent> event) {
    throw new UnsupportedOperationException("Delete from account index not supported");
  }

  public synchronized void indexAsync(Account.Id id, Operation operation) {
    accountsToIndex.put(id, operation);
  }

  public synchronized void doAsyncIndex() {
    accountsToIndex =
        accountsToIndex.entrySet().stream()
            .filter(e -> !checkedIndex(e))
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
  }

  private boolean checkedIndex(Map.Entry<Account.Id, Operation> account) {
    try {
      index(account.getKey(), account.getValue(), Optional.empty());
      return true;
    } catch (IOException e) {
      log.error("Account {} index failed", account.getKey(), e);
      return false;
    }
  }
}
