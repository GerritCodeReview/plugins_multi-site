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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.google.gerrit.server.events.Event;

/** Forward indexing, stream events and cache evictions to the other master */
public interface Forwarder {

  /**
   * Forward a account indexing event to the other master.
   *
   * @param accountId the account to index.
   * @param indexEvent the details of the index event.
   * @return true if successful, otherwise false.
   */
  boolean indexAccount(int accountId, IndexEvent indexEvent);

  /**
   * Forward a change indexing event to the other master.
   *
   * @param projectName the project of the change to index.
   * @param changeId the change to index.
   * @param indexEvent the details of the index event.
   * @return true if successful, otherwise false.
   */
  boolean indexChange(String projectName, int changeId, IndexEvent indexEvent);

  /**
   * Forward a delete change from index event to the other master.
   *
   * @param changeId the change to remove from the index.
   * @param indexEvent the details of the index event.
   * @return rue if successful, otherwise false.
   */
  boolean deleteChangeFromIndex(int changeId, IndexEvent indexEvent);

  /**
   * Forward a group indexing event to the other master.
   *
   * @param uuid the group to index.
   * @param indexEvent the details of the index event.
   * @return true if successful, otherwise false.
   */
  boolean indexGroup(String uuid, IndexEvent indexEvent);

  /**
   * Forward a project indexing event to the other master.
   *
   * @param projectName the project to index.
   * @param indexEvent the details of the index event.
   * @return true if successful, otherwise false.
   */
  boolean indexProject(String projectName, IndexEvent indexEvent);

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
