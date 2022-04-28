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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.server.project.ProjectCache;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ReplicationStatusTest {

  @Mock private ProjectVersionLogger verLogger;
  @Mock private ProjectCache projectCache;
  @Mock private ProjectVersionRefUpdate projectVersionRefUpdate;
  private ReplicationStatus objectUnderTest;
  private Cache<String, Long> replicationStatusCache;

  @Before
  public void setup() throws Exception {
    when(projectCache.all())
        .thenReturn(
            ImmutableSortedSet.of(Project.nameKey("projectA"), Project.nameKey("projectB")));
    replicationStatusCache = CacheBuilder.newBuilder().build();
    objectUnderTest =
        new ReplicationStatus(
            replicationStatusCache, Optional.of(projectVersionRefUpdate), verLogger, projectCache);
  }

  @Test
  public void shouldPopulateLagsFromPersistedCacheOnStart() {
    replicationStatusCache.put("projectA", 10L);
    replicationStatusCache.put("projectB", 3L);

    objectUnderTest.start();
    assertThat(objectUnderTest.getMaxLag()).isEqualTo(10L);
  }

  @Test
  public void shouldBeAbleToUpdatePersistedCacheValues() {
    replicationStatusCache.put("projectA", 3L);

    objectUnderTest.start();

    objectUnderTest.doUpdateLag(Project.nameKey("projectA"), 20L);
    assertThat(objectUnderTest.getMaxLag()).isEqualTo(20L);
  }

  @Test
  public void shouldCombinePersistedProjectsWithNewEntries() {
    replicationStatusCache.put("projectA", 3L);
    objectUnderTest.start();

    objectUnderTest.doUpdateLag(Project.nameKey("projectB"), 20L);

    assertThat(objectUnderTest.getReplicationLags(2).keySet())
        .containsExactly("projectA", "projectB");
  }

  @Test
  public void shouldUpdatePersistedCacheWhenUpdatingLagValue() {
    objectUnderTest.doUpdateLag(Project.nameKey("projectA"), 20L);

    assertThat(replicationStatusCache.getIfPresent("projectA")).isEqualTo(20L);
  }

  @Test
  public void shouldRemoveProjectFromPersistedCache() {
    String projectName = "projectA";
    long lag = 100;
    setupReplicationLag(projectName, lag);
    when(projectVersionRefUpdate.getProjectLocalVersion(projectName)).thenReturn(Optional.empty());

    objectUnderTest.onProjectDeleted(projectDeletedEvent(projectName));

    assertThat(replicationStatusCache.getIfPresent(projectName)).isNull();
  }

  @Test
  public void shouldRemoveProjectFromReplicationLags() {
    String projectName = "projectA";
    long lag = 100;
    setupReplicationLag(projectName, lag);
    when(projectVersionRefUpdate.getProjectLocalVersion(projectName)).thenReturn(Optional.empty());

    assertThat(objectUnderTest.getReplicationLags(1).keySet()).containsExactly(projectName);

    objectUnderTest.onProjectDeleted(projectDeletedEvent(projectName));

    assertThat(objectUnderTest.getReplicationLags(1).keySet()).isEmpty();
  }

  @Test
  public void shouldNotRemoveProjectFromReplicationLagsIfLocalVersionStillExists() {
    String projectName = "projectA";
    long lag = 100;
    setupReplicationLag(projectName, lag);
    when(projectVersionRefUpdate.getProjectLocalVersion(projectName))
        .thenReturn(Optional.of(System.currentTimeMillis()));

    objectUnderTest.onProjectDeleted(projectDeletedEvent(projectName));

    assertThat(objectUnderTest.getReplicationLags(1).keySet()).containsExactly(projectName);
  }

  @Test
  public void shouldNotEvictFromPersistentCacheIfLocalVersionStillExists() {
    String projectName = "projectA";
    long lag = 100;
    setupReplicationLag(projectName, lag);
    when(projectVersionRefUpdate.getProjectLocalVersion(projectName))
        .thenReturn(Optional.of(System.currentTimeMillis()));

    objectUnderTest.onProjectDeleted(projectDeletedEvent(projectName));

    assertThat(replicationStatusCache.getIfPresent(projectName)).isEqualTo(lag);
  }

  private void setupReplicationLag(String projectName, long lag) {
    long currentVersion = System.currentTimeMillis();
    long newVersion = currentVersion + lag;
    replicationStatusCache.put(projectName, 3L);
    when(projectVersionRefUpdate.getProjectRemoteVersion(projectName))
        .thenReturn(Optional.of(newVersion));
    when(projectVersionRefUpdate.getProjectLocalVersion(projectName))
        .thenReturn(Optional.of(currentVersion));
    objectUnderTest.updateReplicationLag(Project.nameKey(projectName));
  }

  private ProjectDeletedListener.Event projectDeletedEvent(String projectName) {
    ProjectDeletedListener.Event event = mock(ProjectDeletedListener.Event.class);
    when(event.getProjectName()).thenReturn(projectName);
    return event;
  }
}
