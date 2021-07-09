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

package com.googlesource.gerrit.plugins.multisite.forwarder.events;

import com.google.common.base.Objects;

public class ProjectListUpdateEvent extends MultiSiteEvent {
  static final String TYPE = "project-list-update";

  public String projectName;
  public boolean remove;

  public ProjectListUpdateEvent(String projectName, boolean remove, String instanceId) {
    super(TYPE, instanceId);
    this.projectName = projectName;
    this.remove = remove;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(projectName, remove);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProjectListUpdateEvent that = (ProjectListUpdateEvent) o;
    return remove == that.remove && Objects.equal(projectName, that.projectName);
  }
}
