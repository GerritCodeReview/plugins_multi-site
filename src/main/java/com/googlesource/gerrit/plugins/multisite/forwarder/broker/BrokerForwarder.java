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

package com.googlesource.gerrit.plugins.multisite.forwarder.broker;

import static com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily.CACHE_EVICTION_EVENT;
import static com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily.INDEX_EVENT;
import static com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily.STREAM_EVENT;

import com.google.gerrit.server.events.Event;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerPublisher;
import com.googlesource.gerrit.plugins.multisite.forwarder.Forwarder;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import org.apache.log4j.Logger;

@Singleton
class BrokerForwarder implements Forwarder {
  private static final Logger log = Logger.getLogger(BrokerForwarder.class);
  private final BrokerPublisher publisher;

  @Inject
  BrokerForwarder(BrokerPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public boolean indexAccount(AccountIndexEvent event) {
    return publisher.publishEvent(INDEX_EVENT, event);
  }

  @Override
  public boolean indexChange(ChangeIndexEvent event) {
    return publisher.publishEvent(INDEX_EVENT, event);
  }

  @Override
  public boolean indexGroup(GroupIndexEvent event) {
    return publisher.publishEvent(INDEX_EVENT, event);
  }

  @Override
  public boolean deleteChangeFromIndex(ChangeIndexEvent event) {
    return publisher.publishEvent(INDEX_EVENT, event);
  }

  @Override
  public boolean indexProject(ProjectIndexEvent event) {
    return publisher.publishEvent(INDEX_EVENT, event);
  }

  @Override
  public boolean send(Event event) {
    return publisher.publishEvent(STREAM_EVENT, event);
  }

  @Override
  public boolean evict(CacheEvictionEvent event) {
    return publisher.publishEvent(CACHE_EVICTION_EVENT, event);
  }

  @Override
  public boolean addToProjectList(String projectName) {
    log.warn("Adding project to cache via broker not yet implemented: " + projectName);
    return false;
  }

  @Override
  public boolean removeFromProjectList(String projectName) {
    log.warn("Remove project from cache via broker not yet implemented: " + projectName);
    return false;
  }
}
