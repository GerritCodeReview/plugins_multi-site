// Copyright (C) 2019 The Android Open Source Project
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

import com.gerritforge.gerrit.globalrefdb.validation.ProjectsFilter;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ProjectListUpdateRouter;

@Singleton
public class ProjectUpdateEventSubscriber extends AbstractSubcriber {
  private final ProjectsFilter projectsFilter;

  @Inject
  public ProjectUpdateEventSubscriber(
      ProjectListUpdateRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      @GerritInstanceId String instanceId,
      MessageLogger msgLog,
      SubscriberMetrics subscriberMetrics,
      Configuration cfg,
      ProjectsFilter projectsFilter) {
    super(eventRouter, droppedEventListeners, instanceId, msgLog, subscriberMetrics, cfg);
    this.projectsFilter = projectsFilter;
  }

  @Override
  protected EventTopic getTopic() {
    return EventTopic.PROJECT_LIST_TOPIC;
  }

  @Override
  protected Boolean shouldConsumeEvent(Event event) {
    return projectsFilter.matches(((ProjectListUpdateEvent) event).projectName);
  }
}
