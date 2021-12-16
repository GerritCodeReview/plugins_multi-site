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
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.IndexEventRouter;

import java.util.Optional;

@Singleton
public class IndexEventSubscriber extends AbstractSubcriber {
  private final ProjectsFilter projectsFilter;
  private final ChangeFinder changeFinder;

  @Inject
  public IndexEventSubscriber(
      IndexEventRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      @GerritInstanceId String instanceId,
      MessageLogger msgLog,
      SubscriberMetrics subscriberMetrics,
      Configuration cfg,
      ProjectsFilter projectsFilter,
      ChangeFinder changeFinder) {
    super(eventRouter, droppedEventListeners, instanceId, msgLog, subscriberMetrics, cfg);
    this.projectsFilter = projectsFilter;
    this.changeFinder = changeFinder;
  }

  @Override
  protected EventTopic getTopic() {
    return EventTopic.INDEX_TOPIC;
  }

  @Override
  protected Boolean shouldConsumeEvent(Event event) {
    if (event instanceof ChangeIndexEvent) {
      ChangeIndexEvent changeIndexEvent = (ChangeIndexEvent) event;
      String projectName = changeIndexEvent.projectName;
      if (isDeletedChangeWithEmptyProject(changeIndexEvent)) {
        projectName = findProjectFromChangeId(changeIndexEvent.changeId).orElse(projectName);
      }
      return projectsFilter.matches(projectName);
    }
    if (event instanceof ProjectIndexEvent) {
      return projectsFilter.matches(((ProjectIndexEvent) event).projectName);
    }
    return true;
  }

  private boolean isDeletedChangeWithEmptyProject(ChangeIndexEvent changeIndexEvent) {
    return changeIndexEvent.deleted && changeIndexEvent.projectName.isEmpty();
  }

  private Optional<String> findProjectFromChangeId(int changeId) {
    return changeFinder.findOne(Change.id(changeId)).map(c -> c.getChange().getProject().get());
  }
}
