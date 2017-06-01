// Copyright (C) 2015 The Android Open Source Project
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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class FileBasedWebSessionCacheCleaner implements LifecycleListener {

  private final WorkQueue queue;
  private final Provider<CleanupTask> cleanupTaskProvider;
  private final long cleanupIntervalMillis;
  private ScheduledFuture<?> scheduledCleanupTask;

  @Inject
  FileBasedWebSessionCacheCleaner(
      WorkQueue queue, Provider<CleanupTask> cleanupTaskProvider, Configuration config) {
    this.queue = queue;
    this.cleanupTaskProvider = cleanupTaskProvider;
    this.cleanupIntervalMillis = config.getCleanupInterval();
  }

  @Override
  public void start() {
    scheduledCleanupTask =
        queue
            .getDefaultQueue()
            .scheduleAtFixedRate(
                cleanupTaskProvider.get(),
                SECONDS.toMillis(1),
                cleanupIntervalMillis,
                MILLISECONDS);
  }

  @Override
  public void stop() {
    if (scheduledCleanupTask != null) {
      scheduledCleanupTask.cancel(true);
      scheduledCleanupTask = null;
    }
  }
}

class CleanupTask implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(CleanupTask.class);
  private final FileBasedWebsessionCache fileBasedWebSessionCache;
  private final String pluginName;

  @Inject
  CleanupTask(FileBasedWebsessionCache fileBasedWebSessionCache, @PluginName String pluginName) {
    this.fileBasedWebSessionCache = fileBasedWebSessionCache;
    this.pluginName = pluginName;
  }

  @Override
  public void run() {
    logger.info("Cleaning up expired file based websessions...");
    fileBasedWebSessionCache.cleanUp();
    logger.info("Cleaning up expired file based websessions...Done");
  }

  @Override
  public String toString() {
    return String.format("[%s] Clean up expired file based websessions", pluginName);
  }
}
