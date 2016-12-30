// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.evictcache;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.git.WorkQueue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EvictCacheExecutorProviderTest {
  private static final String PLUGIN_NAME = "evict-cache";

  @Mock
  private WorkQueue.Executor executorMock;
  private EvictCacheExecutorProvider evictCacheExecutorProvider;

  @Before
  public void setUp() throws Exception {
    WorkQueue workQueueMock = mock(WorkQueue.class);
    when(workQueueMock.createQueue(4,
        "Evict cache [" + PLUGIN_NAME + " plugin]")).thenReturn(executorMock);
    Configuration configMock = mock(Configuration.class);
    when(configMock.getThreadPoolSize()).thenReturn(4);

    evictCacheExecutorProvider =
        new EvictCacheExecutorProvider(workQueueMock, PLUGIN_NAME, configMock);
  }

  @Test
  public void shouldReturnExecutor() throws Exception {
    assertThat(evictCacheExecutorProvider.get()).isEqualTo(executorMock);
  }

  @Test
  public void testStop() throws Exception {
    evictCacheExecutorProvider.start();
    assertThat(evictCacheExecutorProvider.get()).isEqualTo(executorMock);
    evictCacheExecutorProvider.stop();
    verify(executorMock).shutdown();
    verify(executorMock).unregisterWorkQueue();
    assertThat(evictCacheExecutorProvider.get()).isNull();
  }
}
