// Copyright (C) 2017 The Android Open Source Project
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

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexAccountHandler;
import com.google.gerrit.reviewdb.client.Account;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class IndexAccountRestApiServlet extends AbstractIndexRestApiServlet<Account.Id> {
  private static final long serialVersionUID = -1L;

  @Inject
  IndexAccountRestApiServlet(ForwardedIndexAccountHandler handler) {
    super(handler, IndexName.ACCOUNT);
  }

  @Override
  Account.Id parse(String id) {
    return new Account.Id(Integer.parseInt(id));
  }
}
