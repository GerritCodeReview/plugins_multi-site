// Copyright (C) 2020 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.index;

import static com.googlesource.gerrit.plugins.replication.pull.api.PullReplicationEndpoints.APPLY_OBJECT_API_ENDPOINT;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventHandlerTest {

  private static final String INSTANCE_ID = "instance-id";

  private IndexEventHandler eventHandler;

  @Mock private IndexEventForwarder forwarder;
  @Mock private ChangeCheckerImpl.Factory changeChecker;

  @Before
  public void setUp() {
    eventHandler =
        new IndexEventHandler(
            MoreExecutors.directExecutor(),
            asDynamicSet(forwarder),
            changeChecker,
            new TestGroupChecker(true),
            INSTANCE_ID);
  }

  private DynamicSet<IndexEventForwarder> asDynamicSet(IndexEventForwarder forwarder) {
    DynamicSet<IndexEventForwarder> result = new DynamicSet<>();
    result.add("multi-site", forwarder);
    return result;
  }

  @Test
  public void shouldNotForwardIndexChangeIfCurrentThreadIsPullReplicationApplyObject()
      throws Exception {
    String currentThreadName = Thread.currentThread().getName();
    try {
      Thread.currentThread().setName("pull-replication~" + APPLY_OBJECT_API_ENDPOINT);
      int changeId = 1;
      Context.setForwardedEvent(false);
      lenient()
          .when(changeChecker.create(anyString()))
          .thenThrow(
              new IllegalStateException("Change indexing event should have not been triggered"));

      eventHandler.onChangeIndexed("test_project", changeId);
      verifyNoInteractions(changeChecker);
    } finally {
      Thread.currentThread().setName(currentThreadName);
    }
  }
}
