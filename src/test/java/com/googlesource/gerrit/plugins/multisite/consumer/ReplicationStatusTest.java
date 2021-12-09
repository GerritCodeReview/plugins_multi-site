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
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.project.ProjectCache;
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
            replicationStatusCache, projectVersionRefUpdate, verLogger, projectCache);
  }

  @Test
  public void shouldPopulateLagsFromPersistedCache() {
    replicationStatusCache.put("projectA", 10L);
    replicationStatusCache.put("projectB", 3L);

    objectUnderTest.loadFromCache();
    assertThat(objectUnderTest.getMaxLag()).isEqualTo(10L);
  }
}
