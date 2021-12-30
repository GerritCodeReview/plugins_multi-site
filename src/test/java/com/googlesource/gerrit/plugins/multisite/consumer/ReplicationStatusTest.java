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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.multisite.ProjectVersionLogger;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ReplicationStatusTest {

  @Mock private ProjectVersionLogger verLogger;
  @Mock private ProjectCache projectCache;
  @Mock private Provider<ProjectVersionRefUpdate> projectVersionRefUpdateProvider;
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
            replicationStatusCache, projectVersionRefUpdateProvider, verLogger, projectCache);
    lenient().when(projectVersionRefUpdateProvider.get()).thenReturn(projectVersionRefUpdate);
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
}
