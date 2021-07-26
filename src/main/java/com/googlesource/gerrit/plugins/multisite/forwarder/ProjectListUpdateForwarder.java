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

import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;

/** Forward project list update events to the other master */
public interface ProjectListUpdateForwarder {
  /**
   * Forward an update the project list cache event to the other master.
   *
   * @param task that triggered the forwarding of the project list event.
   * @param projectListUpdateEvent the content of project list update event
   * @return true if successful, otherwise false.
   */
  boolean updateProjectList(ForwarderTask task, ProjectListUpdateEvent projectListUpdateEvent);
}
