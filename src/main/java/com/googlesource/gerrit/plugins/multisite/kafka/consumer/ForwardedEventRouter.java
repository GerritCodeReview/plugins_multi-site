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

package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import static com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation.DELETE;
import static com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler.Operation.INDEX;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEntry;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedCacheEvictionHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedEventHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexAccountHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexChangeHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexGroupHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexProjectHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.rest.GsonParser;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class ForwardedEventRouter {
  private final ForwardedCacheEvictionHandler cacheEvictionHanlder;
  private final ForwardedIndexAccountHandler indexAccountHandler;
  private final ForwardedIndexChangeHandler indexChangeHandler;
  private final ForwardedIndexGroupHandler indexGroupHandler;
  private final ForwardedIndexProjectHandler indexProjectHandler;
  private final ForwardedEventHandler streamEventHandler;

  @Inject
  public ForwardedEventRouter(
      ForwardedCacheEvictionHandler cacheEvictionHanlder,
      ForwardedIndexAccountHandler indexAccountHandler,
      ForwardedIndexChangeHandler indexChangeHandler,
      ForwardedIndexGroupHandler indexGroupHandler,
      ForwardedIndexProjectHandler indexProjectHandler,
      ForwardedEventHandler streamEventHandler) {
    this.cacheEvictionHanlder = cacheEvictionHanlder;
    this.indexAccountHandler = indexAccountHandler;
    this.indexChangeHandler = indexChangeHandler;
    this.indexGroupHandler = indexGroupHandler;
    this.indexProjectHandler = indexProjectHandler;
    this.streamEventHandler = streamEventHandler;
  }

  void route(Event sourceEvent)
      throws IOException, OrmException, PermissionBackendException, CacheNotFoundException {
    if (sourceEvent instanceof ChangeIndexEvent) {
      ChangeIndexEvent changeIndexEvent = (ChangeIndexEvent) sourceEvent;
      ForwardedIndexingHandler.Operation operation = changeIndexEvent.deleted ? DELETE : INDEX;
      indexChangeHandler.index(
          changeIndexEvent.projectName + "~" + changeIndexEvent.changeId,
          operation,
          Optional.of(changeIndexEvent));
    } else if (sourceEvent instanceof AccountIndexEvent) {
      AccountIndexEvent accountIndexEvent = (AccountIndexEvent) sourceEvent;
      indexAccountHandler.index(
          new Account.Id(accountIndexEvent.accountId), INDEX, Optional.of(accountIndexEvent));
    } else if (sourceEvent instanceof GroupIndexEvent) {
      GroupIndexEvent groupIndexEvent = (GroupIndexEvent) sourceEvent;
      indexGroupHandler.index(
          AccountGroup.UUID.parse(groupIndexEvent.groupUUID), INDEX, Optional.of(groupIndexEvent));
    } else if (sourceEvent instanceof ProjectIndexEvent) {
      ProjectIndexEvent projectIndexEvent = (ProjectIndexEvent) sourceEvent;
      indexProjectHandler.index(
          new Project.NameKey(projectIndexEvent.projectName),
          INDEX,
          Optional.of(projectIndexEvent));
    } else if (sourceEvent instanceof CacheEvictionEvent) {
      CacheEvictionEvent cacheEvictionEvent = (CacheEvictionEvent) sourceEvent;
      Object parsedKey =
          GsonParser.fromJson(cacheEvictionEvent.cacheName, cacheEvictionEvent.key.toString());
      cacheEvictionHanlder.evict(CacheEntry.from(cacheEvictionEvent.cacheName, parsedKey));
    } else if (sourceEvent instanceof RefEvent) {
      // ForwardedEventHandler can receive any Event subclass but actually just processes subclasses
      // of RefEvent
      streamEventHandler.dispatch(sourceEvent);
    } else {
      throw new UnsupportedOperationException(
          String.format("Cannot route event %s", sourceEvent.getType()));
    }
  }
}
