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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class IndexGroupRestApiServlet extends AbstractIndexRestApiServlet<AccountGroup.UUID> {
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory.getLogger(IndexGroupRestApiServlet.class);

  private final GroupIndexer indexer;

  @Inject
  IndexGroupRestApiServlet(GroupIndexer indexer) {
    super("group");
    this.indexer = indexer;
  }

  @Override
  AccountGroup.UUID parse(String id) {
    return AccountGroup.UUID.parse(id);
  }

  @Override
  void index(AccountGroup.UUID uuid, Operation operation) throws IOException {
    indexer.index(uuid);
    logger.debug("Group {} successfully indexed", uuid);
  }
}
