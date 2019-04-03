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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedIndexAccountHandlerTest {

  @Rule public ExpectedException exception = ExpectedException.none();
  @Mock private AccountIndexer indexerMock;
  @Mock private Configuration config;
  @Mock private Configuration.Index index;
  private ForwardedIndexAccountHandler handler;
  private Account.Id id;

  @Before
  public void setUp() throws Exception {
    when(config.index()).thenReturn(index);
    when(index.numStripedLocks()).thenReturn(10);
    handler = new ForwardedIndexAccountHandler(indexerMock, config);
    id = new Account.Id(123);
  }

  @Test
  public void testSuccessfulIndexing() throws Exception {
    handler.index(id, Operation.INDEX, Optional.empty());
    verify(indexerMock).index(id);
  }

  @Test
  public void deleteIsNotSupported() throws Exception {
    exception.expect(UnsupportedOperationException.class);
    exception.expectMessage("Delete from account index not supported");
    handler.index(id, Operation.DELETE, Optional.empty());
  }
}
