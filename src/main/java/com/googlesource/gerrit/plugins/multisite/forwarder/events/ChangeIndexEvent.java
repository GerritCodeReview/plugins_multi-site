// Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class ChangeIndexEvent extends IndexEvent {
  static final String TYPE = "change-index";

  public String projectName;
  public int changeId;
  public String targetSha;
  public boolean deleted;

  public interface Factory {
    ChangeIndexEvent create(String projectName, int changeId, boolean deleted);
  }

  @AssistedInject
  public ChangeIndexEvent(
      @Assisted String projectName,
      @Assisted int changeId,
      @Assisted boolean deleted,
      @Nullable @GerritInstanceId String gerritInstanceId) {
    super(TYPE);
    this.projectName = projectName;
    this.changeId = changeId;
    this.deleted = deleted;
    this.instanceId = gerritInstanceId;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(projectName, changeId, targetSha, deleted, instanceId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ChangeIndexEvent that = (ChangeIndexEvent) o;
    return changeId == that.changeId
        && deleted == that.deleted
        && Objects.equal(projectName, that.projectName)
        && Objects.equal(targetSha, that.targetSha)
        && Objects.equal(instanceId, that.instanceId);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("instanceId", instanceId)
        .add("eventCreatedOn", format(eventCreatedOn))
        .add("project", projectName)
        .add("changeId", changeId)
        .add("targetSha", targetSha)
        .add("deleted", deleted)
        .toString();
  }

  public static String format(long eventTs) {
    return LocalDateTime.ofEpochSecond(eventTs, 0, ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_DATE_TIME);
  }
}
