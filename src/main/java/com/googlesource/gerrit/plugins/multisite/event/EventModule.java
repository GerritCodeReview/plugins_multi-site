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

package com.googlesource.gerrit.plugins.multisite.event;

import com.gerritforge.gerrit.eventbroker.publisher.StreamEventPublisherConfig;
import com.gerritforge.gerrit.eventbroker.publisher.StreamEventPublisherModule;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.events.EventListener;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.OptionalBinder;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate;
import com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdateImpl;
import java.util.concurrent.Executor;

public class EventModule extends LifecycleModule {
  private final Configuration configuration;

  @Inject
  public EventModule(Configuration configuration) {
    this.configuration = configuration;
  }

  @Override
  protected void configure() {
	  OptionalBinder<ProjectVersionRefUpdate> projectVersionRefUpdateBinder =
		        OptionalBinder.newOptionalBinder(binder(), ProjectVersionRefUpdate.class);
	   if (configuration.getSharedRefDbConfiguration().getSharedRefDb().isEnabled()) {
	        DynamicSet.bind(binder(), EventListener.class).to(ProjectVersionRefUpdateImpl.class);
	        projectVersionRefUpdateBinder
	            .setBinding()
	            .to(ProjectVersionRefUpdateImpl.class)
	            .in(Scopes.SINGLETON);
	      }
	  
    bind(StreamEventPublisherConfig.class)
        .toInstance(
            new StreamEventPublisherConfig(
                EventTopic.STREAM_EVENT_TOPIC.topic(configuration),
                configuration.broker().getStreamEventPublishTimeout()));

    install(new StreamEventPublisherModule());
  }
}
