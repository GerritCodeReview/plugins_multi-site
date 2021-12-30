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

package com.googlesource.gerrit.plugins.multisite.consumer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.eventbroker.EventMessage;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import java.util.Optional;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SubscriberMetricsTest {
  private static final String A_TEST_PROJECT_NAME = "test-project";
  private static final Project.NameKey A_TEST_PROJECT_NAME_KEY =
      Project.nameKey(A_TEST_PROJECT_NAME);

  @Mock private SharedRefDatabaseWrapper sharedRefDb;
  @Mock private GitReferenceUpdated gitReferenceUpdated;
  @Mock private MetricMaker metricMaker;
  @Mock private ProjectVersionLogger verLogger;
  @Mock private ProjectCache projectCache;
  @Mock private Provider<ProjectVersionRefUpdate> projectVersionRefUpdateProvider;
  @Mock private ProjectVersionRefUpdate projectVersionRefUpdate;
  private SubscriberMetrics metrics;
  private EventMessage.Header msgHeader;

  @Before
  public void setup() throws Exception {
    msgHeader = new EventMessage.Header(UUID.randomUUID(), UUID.randomUUID());
    metrics =
        new SubscriberMetrics(
            metricMaker,
            new ReplicationStatus(
                CacheBuilder.newBuilder().build(),
                projectVersionRefUpdateProvider,
                verLogger,
                projectCache));
    when(projectVersionRefUpdateProvider.get()).thenReturn(projectVersionRefUpdate);
  }

  @Test
  public void shouldLogProjectVersionWhenReceivingRefUpdatedEventWithoutLag() {
    Optional<Long> globalRefDbVersion = Optional.of(System.currentTimeMillis() / 1000);
    when(projectVersionRefUpdate.getProjectRemoteVersion(A_TEST_PROJECT_NAME))
        .thenReturn(globalRefDbVersion);
    when(projectVersionRefUpdate.getProjectLocalVersion(A_TEST_PROJECT_NAME))
        .thenReturn(globalRefDbVersion);

    EventMessage eventMessage = new EventMessage(msgHeader, newRefUpdateEvent());

    metrics.updateReplicationStatusMetrics(eventMessage);

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, globalRefDbVersion.get(), 0);
  }

  @Test
  public void shouldLogProjectVersionWhenReceivingRefUpdatedEventWithALag() {
    Optional<Long> globalRefDbVersion = Optional.of(System.currentTimeMillis() / 1000);
    long replicationLag = 60;
    when(projectVersionRefUpdate.getProjectRemoteVersion(A_TEST_PROJECT_NAME))
        .thenReturn(globalRefDbVersion.map(ts -> ts + replicationLag));
    when(projectVersionRefUpdate.getProjectLocalVersion(A_TEST_PROJECT_NAME))
        .thenReturn(globalRefDbVersion);

    EventMessage eventMessage = new EventMessage(msgHeader, newRefUpdateEvent());

    metrics.updateReplicationStatusMetrics(eventMessage);

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, globalRefDbVersion.get(), replicationLag);
  }

  private RefUpdatedEvent newRefUpdateEvent() {
    RefUpdateAttribute refUpdate = new RefUpdateAttribute();
    refUpdate.project = A_TEST_PROJECT_NAME;
    refUpdate.refName = "refs/heads/foo";
    refUpdate.newRev = "591727cfec5174368a7829f79741c41683d84c89";
    RefUpdatedEvent refUpdateEvent = new RefUpdatedEvent();
    refUpdateEvent.refUpdate = Suppliers.ofInstance(refUpdate);
    return refUpdateEvent;
  }
}
