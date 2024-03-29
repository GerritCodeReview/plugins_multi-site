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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.forwarder.IndexEventForwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventHandlerTest {

  private static final String INSTANCE_ID = "instance-id";
  private static final String PROJECT_NAME = "test_project";
  private static final int CHANGE_ID = 1;

  private IndexEventHandler eventHandler;

  @Mock private IndexEventForwarder forwarder;
  @Mock private ChangeCheckerImpl.Factory changeChecker;
  @Mock private ChangeChecker changeCheckerMock;
  @Mock private RequestContext mockCtx;

  private CurrentRequestContext currCtx =
      new CurrentRequestContext(null, null, null) {
        @Override
        public void onlyWithContext(Consumer<RequestContext> body) {
          body.accept(mockCtx);
        }
      };

  @Before
  public void setUp() throws IOException {
    eventHandler =
        new IndexEventHandler(
            MoreExecutors.directExecutor(),
            asDynamicSet(forwarder),
            changeChecker,
            new TestGroupChecker(true),
            INSTANCE_ID,
            currCtx);
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
      Context.setForwardedEvent(false);
      lenient()
          .when(changeChecker.create(anyString()))
          .thenThrow(
              new IllegalStateException("Change indexing event should have not been triggered"));

      eventHandler.onChangeIndexed(PROJECT_NAME, CHANGE_ID);
      verifyNoInteractions(changeChecker);
    } finally {
      Thread.currentThread().setName(currentThreadName);
    }
  }

  @Test
  public void shouldNotForwardIndexChangeWhenContextIsMissingAndForcedIndexingDisabled()
      throws Exception {
    eventHandler = createIndexEventHandler(changeChecker, false);
    eventHandler.onChangeIndexed(PROJECT_NAME, CHANGE_ID);
    verifyNoInteractions(changeChecker);
    verifyNoInteractions(forwarder);
  }

  @Test
  public void shouldForwardIndexChangeWhenContextIsMissingAndForcedIndexingEnabled()
      throws Exception {
    when(changeChecker.create(any())).thenReturn(changeCheckerMock);
    when(changeCheckerMock.newIndexEvent(PROJECT_NAME, CHANGE_ID, false))
        .thenReturn(Optional.of(new ChangeIndexEvent(PROJECT_NAME, CHANGE_ID, false, INSTANCE_ID)));
    eventHandler = createIndexEventHandler(changeChecker, true);
    eventHandler.onChangeIndexed(PROJECT_NAME, CHANGE_ID);
    verify(changeCheckerMock).newIndexEvent(PROJECT_NAME, CHANGE_ID, false);
    verify(forwarder).index(any(), any());
  }

  private IndexEventHandler createIndexEventHandler(
      ChangeCheckerImpl.Factory changeChecker, boolean synchronizeForced) {
    ThreadLocalRequestContext threadLocalCtxMock = mock(ThreadLocalRequestContext.class);
    OneOffRequestContext oneOffCtxMock = mock(OneOffRequestContext.class);
    Configuration cfgMock = mock(Configuration.class);
    Configuration.Index cfgIndex = mock(Configuration.Index.class);
    when(cfgMock.index()).thenReturn(cfgIndex);
    when(cfgIndex.synchronizeForced()).thenReturn(synchronizeForced);
    return new IndexEventHandler(
        MoreExecutors.directExecutor(),
        asDynamicSet(forwarder),
        changeChecker,
        new TestGroupChecker(true),
        INSTANCE_ID,
        new CurrentRequestContext(threadLocalCtxMock, cfgMock, oneOffCtxMock));
  }
}
