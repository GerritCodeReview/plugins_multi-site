// Copyright (C) 2017 Ericsson
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

/**
 * Forward event to the other master
 */
public interface EventForwarder {

  /**
   * Forward a change indexing event to the other master.
   *
   * @param changeId the change to index.
   * @return true if successful, otherwise false.
   */
  boolean indexChange(int changeId);

  /**
   * Forward a delete change from index event to the other master.
   *
   * @param changeId the change to remove from the index.
   * @return rue if successful, otherwise false.
   */
  boolean deleteChangeFromIndex(int changeId);

  /**
   * Forward an event to the other master.
   *
   * @param event the event to forward.
   * @return true if successful, otherwise false.
   */
  boolean send(Event event);
}
