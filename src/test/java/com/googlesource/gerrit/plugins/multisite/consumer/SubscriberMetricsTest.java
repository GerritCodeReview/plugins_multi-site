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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import com.googlesource.gerrit.plugins.replication.events.ProjectDeletionReplicationSucceededEvent;
import java.net.URISyntaxException;
import java.util.Optional;
import org.eclipse.jgit.transport.URIish;
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

  @Mock private MetricMaker metricMaker;
  @Mock private ProjectVersionLogger verLogger;
  @Mock private ProjectCache projectCache;
  @Mock private Provider<ProjectVersionRefUpdate> projectVersionRefUpdateProvider;
  @Mock private ProjectVersionRefUpdate projectVersionRefUpdate;
  private SubscriberMetrics metrics;
  private ReplicationStatus replicationStatus;

  @Before
  public void setup() throws Exception {
    replicationStatus =
        new ReplicationStatus(
            CacheBuilder.newBuilder().build(),
            projectVersionRefUpdateProvider,
            verLogger,
            projectCache);
    metrics = new SubscriberMetrics(metricMaker, replicationStatus);
    when(projectVersionRefUpdateProvider.get()).thenReturn(projectVersionRefUpdate);
  }

  @Test
  public void shouldLogProjectVersionWhenReceivingRefUpdatedEventWithoutLag() {
    Optional<Long> globalRefDbVersion = Optional.of(System.currentTimeMillis() / 1000);
    when(projectVersionRefUpdate.getProjectRemoteVersion(A_TEST_PROJECT_NAME))
        .thenReturn(globalRefDbVersion);
    when(projectVersionRefUpdate.getProjectLocalVersion(A_TEST_PROJECT_NAME))
        .thenReturn(globalRefDbVersion);

    Event eventMessage = newRefUpdateEvent();

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

    Event eventMessage = newRefUpdateEvent();

    metrics.updateReplicationStatusMetrics(eventMessage);

    verify(verLogger).log(A_TEST_PROJECT_NAME_KEY, globalRefDbVersion.get(), replicationLag);
  }

  @Test
  public void
      shouldLogUponProjectDeletionSuccessWhenLocalVersionDoesNotExistAndSubscriberMetricsExist()
          throws Exception {
    long nowSecs = System.currentTimeMillis() / 1000;
    long replicationLagSecs = 60;
    Optional<Long> globalRefDbVersion = Optional.of(nowSecs);
    when(projectVersionRefUpdate.getProjectRemoteVersion(A_TEST_PROJECT_NAME))
        .thenReturn(globalRefDbVersion.map(ts -> ts + replicationLagSecs));
    when(projectVersionRefUpdate.getProjectLocalVersion(A_TEST_PROJECT_NAME))
        .thenReturn(globalRefDbVersion);

    Event refUpdateEventMessage = newRefUpdateEvent();
    metrics.updateReplicationStatusMetrics(refUpdateEventMessage);

    assertThat(replicationStatus.getReplicationStatus(A_TEST_PROJECT_NAME))
        .isEqualTo(replicationLagSecs);
    assertThat(replicationStatus.getLocalVersion(A_TEST_PROJECT_NAME)).isEqualTo(nowSecs);

    when(projectVersionRefUpdate.getProjectLocalVersion(A_TEST_PROJECT_NAME))
        .thenReturn(Optional.empty());

    Event projectDeleteEventMessage = projectDeletionSuccess();
    metrics.updateReplicationStatusMetrics(projectDeleteEventMessage);

    verify(verLogger).logDeleted(A_TEST_PROJECT_NAME_KEY);
  }

  @Test
  public void shouldNotLogUponProjectDeletionSuccessWhenSubscriberMetricsDoNotExist()
      throws Exception {
    Event eventMessage = projectDeletionSuccess();
    when(projectVersionRefUpdate.getProjectLocalVersion(A_TEST_PROJECT_NAME))
        .thenReturn(Optional.empty());

    assertThat(replicationStatus.getReplicationStatus(A_TEST_PROJECT_NAME)).isNull();
    assertThat(replicationStatus.getLocalVersion(A_TEST_PROJECT_NAME)).isNull();

    metrics.updateReplicationStatusMetrics(eventMessage);

    verifyZeroInteractions(verLogger);
  }

  @Test
  public void shouldNotLogUponProjectDeletionSuccessWhenLocalVersionStillExists() throws Exception {
    Event eventMessage = projectDeletionSuccess();
    Optional<Long> anyRefVersionValue = Optional.of(System.currentTimeMillis() / 1000);
    when(projectVersionRefUpdate.getProjectLocalVersion(A_TEST_PROJECT_NAME))
        .thenReturn(anyRefVersionValue);

    metrics.updateReplicationStatusMetrics(eventMessage);

    verifyZeroInteractions(verLogger);
  }

  @Test
  public void shouldRemoveProjectMetricsUponProjectDeletionSuccess() throws Exception {
    long nowSecs = System.currentTimeMillis() / 1000;
    long replicationLagSecs = 60;
    Optional<Long> globalRefDbVersion = Optional.of(nowSecs);
    when(projectVersionRefUpdate.getProjectRemoteVersion(A_TEST_PROJECT_NAME))
        .thenReturn(globalRefDbVersion.map(ts -> ts + replicationLagSecs));
    when(projectVersionRefUpdate.getProjectLocalVersion(A_TEST_PROJECT_NAME))
        .thenReturn(globalRefDbVersion);

    Event eventMessage = newRefUpdateEvent();

    metrics.updateReplicationStatusMetrics(eventMessage);

    assertThat(replicationStatus.getReplicationStatus(A_TEST_PROJECT_NAME))
        .isEqualTo(replicationLagSecs);
    assertThat(replicationStatus.getLocalVersion(A_TEST_PROJECT_NAME)).isEqualTo(nowSecs);

    when(projectVersionRefUpdate.getProjectLocalVersion(A_TEST_PROJECT_NAME))
        .thenReturn(Optional.empty());
    Event projectDeleteEvent = projectDeletionSuccess();

    metrics.updateReplicationStatusMetrics(projectDeleteEvent);

    assertThat(replicationStatus.getReplicationStatus(A_TEST_PROJECT_NAME)).isNull();
    assertThat(replicationStatus.getLocalVersion(A_TEST_PROJECT_NAME)).isNull();
  }

  private ProjectDeletionReplicationSucceededEvent projectDeletionSuccess()
      throws URISyntaxException {
    return new ProjectDeletionReplicationSucceededEvent(
        A_TEST_PROJECT_NAME, new URIish("git://target"));
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
