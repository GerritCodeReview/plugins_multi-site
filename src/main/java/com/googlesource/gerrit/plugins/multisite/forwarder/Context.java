// Copyright (C) 2015 The Android Open Source Project
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

import static com.googlesource.gerrit.plugins.replication.pull.api.PullReplicationEndpoints.APPLY_OBJECTS_API_ENDPOINT;
import static com.googlesource.gerrit.plugins.replication.pull.api.PullReplicationEndpoints.APPLY_OBJECT_API_ENDPOINT;

/** Allows to tag a forwarded event to avoid infinitely looping events. */
public class Context {
  public static final String PULL_REPLICATION_PLUGIN_NAME = "pull-replication";
  private static final ThreadLocal<Boolean> forwardedEvent = ThreadLocal.withInitial(() -> false);

  private Context() {}

  public static Boolean isForwardedEvent() {
    return forwardedEvent.get()
        ||
        // When the event is a result of pull-replication event, is considered as
        // "forwarded" action because did not happen on this node.
        isPullReplicationApplyObjectIndexing();
  }

  public static void setForwardedEvent(Boolean b) {
    forwardedEvent.set(b);
  }

  public static void unsetForwardedEvent() {
    forwardedEvent.remove();
  }

  public static boolean isPullReplicationApplyObjectIndexing() {
    String threadName = Thread.currentThread().getName();
    return threadName.contains(PULL_REPLICATION_PLUGIN_NAME + "~" + APPLY_OBJECT_API_ENDPOINT)
        || threadName.contains(PULL_REPLICATION_PLUGIN_NAME + "~" + APPLY_OBJECTS_API_ENDPOINT);
  }
}
