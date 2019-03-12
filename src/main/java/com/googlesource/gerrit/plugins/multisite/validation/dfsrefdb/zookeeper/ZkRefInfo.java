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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import com.google.common.base.Objects;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

public class ZkRefInfo {

  private final String refName;
  private final String projectName;
  private final ObjectId objectId;

  public ZkRefInfo(final String projectName, final String refName, final ObjectId objectId) {
    this.projectName = projectName;
    this.objectId = objectId;
    this.refName = refName;
  }

  public ZkRefInfo(final String projectName, final Ref ref) {
    this(projectName, ref.getName(), ref.getObjectId());
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ZkRefInfo zkRefInfo = (ZkRefInfo) other;
    return Objects.equal(refName, zkRefInfo.refName)
        && Objects.equal(projectName, zkRefInfo.projectName)
        && Objects.equal(objectId, zkRefInfo.objectId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(refName, projectName, objectId);
  }

  public String refName() {
    return refName;
  }

  public String projectName() {
    return projectName;
  }

  public ObjectId objectId() {
    return objectId;
  }
}
