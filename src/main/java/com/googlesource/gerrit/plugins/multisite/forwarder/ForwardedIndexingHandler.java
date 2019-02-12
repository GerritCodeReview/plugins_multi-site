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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import com.google.common.util.concurrent.Striped;
import com.google.gwtorm.server.OrmException;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class to handle forwarded indexing. This class is meant to be extended by classes used on
 * the receiving side of the {@link Forwarder} since it will prevent indexing to be forwarded again
 * causing an infinite forwarding loop between the 2 nodes. It will also make sure no concurrent
 * indexing is done for the same id.
 */
public abstract class ForwardedIndexingHandler<T> {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  public enum Operation {
    INDEX,
    DELETE;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  private final Striped<Lock> idLocks;

  protected abstract void doIndex(T id, Optional<ChangeIndexEvent> indexEvent)
      throws IOException, OrmException;

  protected abstract void doDelete(T id, Optional<ChangeIndexEvent> indexEvent) throws IOException;

  protected ForwardedIndexingHandler(int lockStripes) {
    idLocks = Striped.lock(lockStripes);
  }

  /**
   * Index an item in the local node, indexing will not be forwarded to the other node.
   *
   * @param id The id to index.
   * @param operation The operation to do; index or delete
   * @param indexEvent The index event details.
   * @throws IOException If an error occur while indexing.
   * @throws OrmException If an error occur while retrieving a change related to the item to index
   */
  public void index(T id, Operation operation, Optional<ChangeIndexEvent> indexEvent)
      throws IOException, OrmException {
    log.debug("{} {} {}", operation, id, indexEvent);
    try {
      Context.setForwardedEvent(true);
      Lock idLock = idLocks.get(id);
      idLock.lock();
      try {
        switch (operation) {
          case INDEX:
            doIndex(id, indexEvent);
            break;
          case DELETE:
            doDelete(id, indexEvent);
            break;
          default:
            log.error("unexpected operation: {}", operation);
            break;
        }
      } finally {
        idLock.unlock();
      }
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}
