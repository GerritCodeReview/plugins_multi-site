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

package com.ericsson.gerrit.plugins.syncevents;

import com.google.gerrit.common.EventListener;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.inject.Inject;

import java.util.concurrent.ScheduledThreadPoolExecutor;

class EventHandler implements EventListener {
  private final ScheduledThreadPoolExecutor executor;
  private final RestSession restClient;
  private final String pluginName;

  @Inject
  EventHandler(RestSession restClient,
      @SyncEventExecutor ScheduledThreadPoolExecutor executor,
      @PluginName String pluginName) {
    this.restClient = restClient;
    this.executor = executor;
    this.pluginName = pluginName;
  }

  @Override
  public void onEvent(Event event) {
    if (!Context.isForwardedEvent() && event instanceof ProjectEvent) {
      executor.execute(new SyncEventTask(event));
    }
  }

  class SyncEventTask implements Runnable {
    private Event event;

    SyncEventTask(Event event) {
      this.event = event;
    }

    @Override
    public void run() {
      restClient.send(event);
    }

    @Override
    public String toString() {
      return String.format("[%s] Send event '%s' to target instance",
          pluginName, event.type);
    }
  }
}
