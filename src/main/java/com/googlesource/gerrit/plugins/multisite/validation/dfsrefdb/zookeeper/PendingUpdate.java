
// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.primitives.Ints;

import java.util.List;

/** Data about pending ref updates, stored in Zookeeper nodes. */
class PendingUpdate implements Comparable<PendingUpdate> {
    /** Version of the repository <em>after</em> applying this update. */
    int repoVersion;

    /** Path at which this update is stored in Zookeeper. */
    transient String path;

    /** Reference name. */
    String refName;

    /** Old hex SHA of ref. */
    String oldId;

    /** New hex SHA of ref. */
    String newId;

    /** Replicas that are known to have the objects required for this update. */
    List<String> sourceReplicas;

    @Override
    public int compareTo(PendingUpdate other) {
        return Ints.compare(repoVersion, other.repoVersion);
    }
}
