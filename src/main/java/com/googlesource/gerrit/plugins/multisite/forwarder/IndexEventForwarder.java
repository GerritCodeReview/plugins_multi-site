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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import com.googlesource.gerrit.plugins.multisite.forwarder.events.IndexEvent;

public interface IndexEventForwarder {

  /**
   * Publish an indexing event to the broker using interactive topic.
   *
   * @param task that triggered the forwarding of the index event.
   * @param event the details of the index event.
   * @return true if successful, otherwise false.
   */
  boolean index(ForwarderTask task, IndexEvent event);

  /**
   * Publish an indexing event to the broker using batch topic.
   *
   * @param task that triggered the forwarding of the index event.
   * @param event the details of the index event.
   * @return true if successful, otherwise false.
   */
  boolean batchIndex(ForwarderTask task, IndexEvent event);
}
