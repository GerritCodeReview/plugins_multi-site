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

package com.googlesource.gerrit.plugins.multisite.forwarder.router;

import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedEventHandler;
import com.googlesource.gerrit.plugins.replication.events.RefReplicationDoneEvent;
import java.io.IOException;

public class StreamEventRouter implements ForwardedEventRouter<Event> {
  private final ForwardedEventHandler streamEventHandler;
  private final IndexEventRouter indexEventRouter;

  @Inject
  public StreamEventRouter(
      ForwardedEventHandler streamEventHandler, IndexEventRouter indexEventRouter) {
    this.streamEventHandler = streamEventHandler;
    this.indexEventRouter = indexEventRouter;
  }

  @Override
  public void route(Event sourceEvent) throws PermissionBackendException, IOException {
    if (RefReplicationDoneEvent.TYPE.equals(sourceEvent.getType())) {
      /* TODO: We currently explicitly ignore the status and result of the replication
       * event because there isn't a reliable way to understand if the current node was
       * the replication target and was successful or not.
       *
       * It is better to risk to reindex once more rather than missing a reindexing event.
       */
      indexEventRouter.onRefReplicated((RefReplicationDoneEvent) sourceEvent);
    }

    streamEventHandler.dispatch(sourceEvent);
  }
}
