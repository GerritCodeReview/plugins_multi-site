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

package com.googlesource.gerrit.plugins.multisite;

import com.gerritforge.gerrit.globalrefdb.validation.ProjectDeletedSharedDbCleanup;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApiWrapper;
import com.googlesource.gerrit.plugins.multisite.consumer.MultiSiteConsumerRunner;
import com.googlesource.gerrit.plugins.multisite.consumer.ReplicationStatusModule;
import com.googlesource.gerrit.plugins.multisite.consumer.SubscriberModule;
import com.googlesource.gerrit.plugins.multisite.event.EventModule;
import com.googlesource.gerrit.plugins.multisite.forwarder.broker.BrokerForwarderModule;

public class PluginModule extends LifecycleModule {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  public static final String PULL_REPLICATION_FILTER_MODULE =
      "com.googlesource.gerrit.plugins.multisite.validation.PullReplicationFilterModule";
  public static final String PUSH_REPLICATION_FILTER_MODULE =
      "com.googlesource.gerrit.plugins.multisite.validation.PushReplicationFilterModule";

  private final Configuration config;
  private final WorkQueue workQueue;
  private final Injector parentInjector;

  @Inject
  public PluginModule(Configuration config, WorkQueue workQueue, Injector parentInjector) {
    this.config = config;
    this.workQueue = workQueue;
    this.parentInjector = parentInjector;
  }

  @Override
  protected void configure() {
    if (config.index().synchronize()
        || config.cache().synchronize()
        || config.event().synchronize()) {
      install(new EventModule(config));
      bind(BrokerApiWrapper.class).in(Scopes.SINGLETON);
      install(new SubscriberModule());

      install(new BrokerForwarderModule());
      listener().to(MultiSiteConsumerRunner.class);

      install(new ReplicationStatusModule(workQueue));
    }

    if (config.getSharedRefDbConfiguration().getSharedRefDb().isEnabled()) {
      listener().to(PluginStartup.class);
      DynamicSet.bind(binder(), ProjectDeletedListener.class)
          .to(ProjectDeletedSharedDbCleanup.class);
    }

    detectFilterModules()
        .forEach(
            mod -> {
              install(mod);
              log.atInfo().log(
                  "Replication filter module %s installed successfully",
                  mod.getClass().getSimpleName());
            });
  }

  private Iterable<AbstractModule> detectFilterModules() {
    ImmutableList.Builder<AbstractModule> filterModulesBuilder = ImmutableList.builder();

    if (config.pushReplicationFilterEnabled()) {
      if(!bindReplicationFilterClass(PUSH_REPLICATION_FILTER_MODULE, filterModulesBuilder)) {
        config.disablePushReplicationFilter();
      }
    }
    if (config.pullRepllicationFilterEnabled()) {
      if(!bindReplicationFilterClass(PULL_REPLICATION_FILTER_MODULE, filterModulesBuilder)) {
        config.disablePullReplicationFilter();
      }

    }

    return filterModulesBuilder.build();
  }

  private boolean bindReplicationFilterClass(
      String filterClassName, ImmutableList.Builder<AbstractModule> filterModulesBuilder) {
    try {
      @SuppressWarnings("unchecked")
      Class<AbstractModule> filterClass = (Class<AbstractModule>) Class.forName(filterClassName);

      AbstractModule filterModule = parentInjector.getInstance(filterClass);
      // Check if the filterModule would be valid for creating a child Guice Injector
      parentInjector.createChildInjector(filterModule);

      filterModulesBuilder.add(filterModule);
      return true;
    } catch (NoClassDefFoundError | ClassNotFoundException e) {
      log.atWarning().withCause(e).log(
          "Not loading %s because of missing the associated replication plugin", filterClassName);
      return false;
    } catch (Exception e) {
      throw new ProvisionException(
          "Unable to instantiate replication filter " + filterClassName, e);
    }
  }
}
