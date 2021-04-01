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

import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.IndexEvent;
import com.googlesource.gerrit.plugins.multisite.index.Checker;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Base class to handle forwarded indexing. This class is meant to be extended by classes used on
 * the receiving side of the {@link E} since it will prevent indexing to be forwarded again causing
 * an infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same id.
 */
public abstract class ForwardedIndexingHandlerWithRetries<T, E extends IndexEvent>
    extends ForwardedIndexingHandler<T, E> {

  private final int retryInterval;
  private final int maxTries;
  private final ScheduledExecutorService indexExecutor;
  protected final OneOffRequestContext oneOffCtx;

  ForwardedIndexingHandlerWithRetries(
      ScheduledExecutorService indexExecutor,
      Configuration configuration,
      OneOffRequestContext oneOffCtx) {
    super(configuration.index().numStripedLocks());

    Configuration.Index indexConfig = configuration.index();
    this.oneOffCtx = oneOffCtx;
    this.indexExecutor = indexExecutor;
    this.retryInterval = indexConfig != null ? indexConfig.retryInterval() : 0;
    this.maxTries = indexConfig != null ? indexConfig.maxTries() : 0;
  }

  protected abstract void reindex(T id);

  protected abstract String label();

  protected abstract void attemptToIndex(T id, Optional<E> indexEvent, int retryCount);

  protected boolean rescheduleIndex(T id, Optional<E> indexEvent, int retryCount) {
    if (retryCount > maxTries) {
      log.error(
          "{} {} could not be indexed after {} retries. {} index could be stale.",
          label(),
          id,
          retryCount,
          label());
      return false;
    }

    log.warn(
        "Retrying for the #{} time to index {} {} after {} msecs",
        retryCount,
        label(),
        id,
        retryInterval);
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        indexExecutor.schedule(
            () -> {
              try (ManualRequestContext ctx = oneOffCtx.open()) {
                Context.setForwardedEvent(true);
                attemptToIndex(id, indexEvent, retryCount);
              } catch (Exception e) {
                log.warn("{} {} could not be indexed", label(), id, e);
              }
            },
            retryInterval,
            TimeUnit.MILLISECONDS);
    return true;
  }

  public final void checkOrReschedule(
      T id, Optional<E> indexEvent, Checker<E> checker, int retryCount) {
    reindex(id);
    if (checker.isUpToDate(indexEvent)) {
      if (retryCount > 0) {
        log.warn("{} {} has been eventually indexed after {} attempt(s)", label(), id, retryCount);
      } else {
        log.debug("{} {} successfully indexed", label(), id);
      }
    } else {
      log.warn("{} {} is not up-to-date. Rescheduling", label(), id);
      rescheduleIndex(id, indexEvent, retryCount + 1);
    }
  }
}
