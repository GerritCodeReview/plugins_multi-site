// Copyright (C) 2017 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability.websession.file;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.inject.Provider;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FileBasedWebSessionCacheCleanerTest {

  private static long CLEANUP_INTERVAL = 5000;
  private static String SOME_PLUGIN_NAME = "somePluginName";

  @Mock private Executor executorMock;
  @Mock private ScheduledFuture<?> scheduledFutureMock;
  @Mock private WorkQueue workQueueMock;
  @Mock private Provider<CleanupTask> cleanupTaskProviderMock;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Configuration configMock;

  private FileBasedWebSessionCacheCleaner cleaner;

  @Before
  public void setUp() {
    when(cleanupTaskProviderMock.get()).thenReturn(new CleanupTask(null, null));
    when(workQueueMock.getDefaultQueue()).thenReturn(executorMock);
    doReturn(scheduledFutureMock)
        .when(executorMock)
        .scheduleAtFixedRate(isA(CleanupTask.class), anyLong(), anyLong(), isA(TimeUnit.class));
    when(configMock.websession().cleanupInterval()).thenReturn(CLEANUP_INTERVAL);
    cleaner =
        new FileBasedWebSessionCacheCleaner(workQueueMock, cleanupTaskProviderMock, configMock);
  }

  @Test
  public void testCleanupTaskRun() {
    FileBasedWebsessionCache cacheMock = mock(FileBasedWebsessionCache.class);
    CleanupTask task = new CleanupTask(cacheMock, null);
    int numberOfRuns = 5;
    for (int i = 0; i < numberOfRuns; i++) {
      task.run();
    }
    verify(cacheMock, times(numberOfRuns)).cleanUp();
  }

  @Test
  public void testCleanupTaskToString() {
    CleanupTask task = new CleanupTask(null, SOME_PLUGIN_NAME);
    assertThat(task.toString())
        .isEqualTo(String.format("[%s] Clean up expired file based websessions", SOME_PLUGIN_NAME));
  }

  @Test
  public void testCleanupTaskIsScheduledOnStart() {
    cleaner.start();
    verify(executorMock, times(1))
        .scheduleAtFixedRate(
            isA(CleanupTask.class), eq(1000l), eq(CLEANUP_INTERVAL), eq(TimeUnit.MILLISECONDS));
  }

  @Test
  public void testCleanupTaskIsCancelledOnStop() {
    cleaner.start();
    cleaner.stop();
    verify(scheduledFutureMock, times(1)).cancel(true);
  }

  @Test
  public void testCleanupTaskIsCancelledOnlyOnce() {
    cleaner.start();
    cleaner.stop();
    cleaner.stop();
    verify(scheduledFutureMock, times(1)).cancel(true);
  }
}
