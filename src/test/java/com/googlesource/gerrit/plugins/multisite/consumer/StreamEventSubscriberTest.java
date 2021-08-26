// Copyright (C) 2021 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.IndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.StreamEventRouter;
import com.googlesource.gerrit.plugins.replication.events.RefReplicatedEvent;
import com.googlesource.gerrit.plugins.replication.events.RefReplicationDoneEvent;
import com.googlesource.gerrit.plugins.replication.events.ReplicationScheduledEvent;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.mockito.Mock;

public class StreamEventSubscriberTest extends AbstractSubscriberTestBase {
  private static final NameKey PROJECT_NAME_KEY = NameKey.parse(PROJECT_NAME);
  private @Mock RefUpdatedEvent refUpdatedEvent;
  private @Mock RefReplicationDoneEvent refReplicationDoneEvent;
  private @Mock ReplicationScheduledEvent replicationScheduledEvent;
  private @Mock RefReplicatedEvent refReplicatedEvent;

  @SuppressWarnings("rawtypes")
  @Override
  protected ForwardedEventRouter eventRouter() {
    return mock(StreamEventRouter.class);
  }

  @Override
  protected List<Event> events() {
    when(refUpdatedEvent.getProjectNameKey()).thenReturn(PROJECT_NAME_KEY);
    refUpdatedEvent.instanceId = INSTANCE_ID;
    when(refReplicationDoneEvent.getProjectNameKey()).thenReturn(PROJECT_NAME_KEY);
    refReplicationDoneEvent.instanceId = INSTANCE_ID;
    when(replicationScheduledEvent.getProjectNameKey()).thenReturn(PROJECT_NAME_KEY);
    replicationScheduledEvent.instanceId = INSTANCE_ID;
    when(refReplicatedEvent.getProjectNameKey()).thenReturn(PROJECT_NAME_KEY);
    refReplicatedEvent.instanceId = INSTANCE_ID;

    return ImmutableList.of(
        refUpdatedEvent, refReplicationDoneEvent, replicationScheduledEvent, refReplicatedEvent);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldNotConsumeNonProjectEventTypeEvents()
      throws IOException, PermissionBackendException, CacheNotFoundException {
    IndexEvent event = new AccountIndexEvent(1, INSTANCE_ID);

    objectUnderTest.getConsumer().accept(event);

    verify(projectsFilter, never()).matches(PROJECT_NAME);
    verify(eventRouter, times(1)).route(event);
    verify(droppedEventListeners, never()).onEventDropped(event);
  }

  @Override
  protected AbstractSubcriber objectUnderTest() {
    return new StreamEventSubscriber(
        (StreamEventRouter) eventRouter,
        asDynamicSet(droppedEventListeners),
        NODE_INSTANCE_ID,
        msgLog,
        subscriberMetrics,
        cfg,
        projectsFilter);
  }
}
