// Copyright (C) 2017 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import com.google.gerrit.server.events.Event;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;

/** Forward indexing, stream events and cache evictions to the other master */
public interface Forwarder {

  /**
   * Forward a account indexing event to the other master.
   *
   * @param accountIndexEvent the details of the account index event.
   * @return true if successful, otherwise false.
   */
  boolean indexAccount(AccountIndexEvent accountIndexEvent);

  /**
   * Forward a change indexing event to the other master.
   *
   * @param changeIndexEvent the details of the change index event.
   * @return true if successful, otherwise false.
   */
  boolean indexChange(ChangeIndexEvent changeIndexEvent);

  /**
   * Forward a delete change from index event to the other master.
   *
   * @param changeIndexEvent the details of the change index event.
   * @return rue if successful, otherwise false.
   */
  boolean deleteChangeFromIndex(ChangeIndexEvent changeIndexEvent);

  /**
   * Forward a group indexing event to the other master.
   *
   * @param groupIndexEvent the details of the index event.
   * @return true if successful, otherwise false.
   */
  boolean indexGroup(GroupIndexEvent groupIndexEvent);

  /**
   * Forward a project indexing event to the other master.
   *
   * @param projectIndexEvent the details of the index event.
   * @return true if successful, otherwise false.
   */
  boolean indexProject(ProjectIndexEvent projectIndexEvent);

  /**
   * Forward a stream event to the other master.
   *
   * @param event the event to forward.
   * @return true if successful, otherwise false.
   */
  boolean send(Event event);

  /**
   * Forward a cache eviction event to the other master.
   *
   * @param cacheName the name of the cache to evict an entry from.
   * @param key the key identifying the entry to evict from the cache.
   * @return true if successful, otherwise false.
   */
  boolean evict(String cacheName, Object key);

  /**
   * Forward an addition to the project list cache to the other master.
   *
   * @param projectName the name of the project to add to the project list cache
   * @return true if successful, otherwise false.
   */
  boolean addToProjectList(String projectName);

  /**
   * Forward a removal from the project list cache to the other master.
   *
   * @param projectName the name of the project to remove from the project list cache
   * @return true if successful, otherwise false.
   */
  boolean removeFromProjectList(String projectName);
}
