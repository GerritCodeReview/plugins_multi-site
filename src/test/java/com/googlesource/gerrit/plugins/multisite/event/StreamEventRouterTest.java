// Copyright (C) 2019 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.event;

import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.util.time.TimeUtil;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedEventHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.IndexEventRouter;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.StreamEventRouter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StreamEventRouterTest {

  private StreamEventRouter router;
  @Mock private ForwardedEventHandler streamEventHandler;
  @Mock private IndexEventRouter indexEventRouter;

  @Before
  public void setUp() {
    router = new StreamEventRouter(streamEventHandler, indexEventRouter);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_StreamEvent() throws Exception {
    final CommentAddedEvent event = new CommentAddedEvent(aChange());
    router.route(event);
    verify(streamEventHandler).dispatch(event);
  }

  private Change aChange() {
    return new Change(
        Change.key("Iabcd1234abcd1234abcd1234abcd1234abcd1234"),
        Change.id(1),
        Account.id(1),
        BranchNameKey.create("proj", "refs/heads/master"),
        TimeUtil.now());
  }
}
