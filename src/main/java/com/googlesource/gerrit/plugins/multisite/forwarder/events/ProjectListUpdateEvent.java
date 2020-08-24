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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

public class ProjectListUpdateEvent extends MultiSiteEvent {
  static final String TYPE = "project-list-update";

  public String projectName;
  public boolean remove;

  public interface Factory {
    ProjectListUpdateEvent create(String projectName, boolean remove);
  }

  @AssistedInject
  public ProjectListUpdateEvent(
      @Assisted String projectName,
      @Assisted boolean remove,
      @Nullable @GerritInstanceId String gerritInstanceId) {
    super(TYPE);
    this.projectName = projectName;
    this.remove = remove;
    this.instanceId = gerritInstanceId;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(projectName, remove, instanceId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProjectListUpdateEvent that = (ProjectListUpdateEvent) o;
    return remove == that.remove
        && Objects.equal(projectName, that.projectName)
        && Objects.equal(instanceId, that.instanceId);
  }
}
