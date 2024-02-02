// Copyright (C) 2021 The Android Open Source Project
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
import com.googlesource.gerrit.plugins.multisite.index.UpToDateChecker;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Base class to handle forwarded indexing. This class is meant to be extended by classes used on
 * the receiving side of the {@link IndexEvent} since it will prevent indexing to be forwarded again
 * causing an infinite forwarding loop between the 2 nodes. It will also make sure no concurrent
 * indexing is done for the same id.
 */
public abstract class ForwardedIndexingHandlerWithRetries<T, E extends IndexEvent>
    extends ForwardedIndexingHandler<T, E> {

  private final int retryInterval;
  private final int maxTries;
  private final ScheduledExecutorService indexExecutor;
  protected final OneOffRequestContext oneOffCtx;
  protected final Map<T, IndexingRetry> indexingRetryTaskMap = new ConcurrentHashMap<>();

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

  protected abstract String indexName();

  protected abstract void attemptToIndex(T id, IndexingRetry retry);

  protected boolean rescheduleIndex(T id) {
    IndexingRetry retry = indexingRetryTaskMap.get(id);
    if (retry == null) {
      log.warn(
          "{} {} successfully indexed by different task. Not rescheduling the task",
          indexName(),
          id);
      return true;
    }
    if (retry.getRetryNumber() > maxTries) {
      log.error(
          "{} {} could not be indexed after {} retries. {} index could be stale.",
          indexName(),
          id,
          retry.getRetryNumber(),
          indexName());
      indexingRetryTaskMap.remove(id);
      return false;
    }

    log.warn(
        "Retrying for the #{} time to index {} {} after {} msecs",
        retry.getRetryNumber(),
        indexName(),
        id,
        retryInterval);
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        indexExecutor.schedule(
            () -> {
              try (ManualRequestContext ctx = oneOffCtx.open()) {
                Context.setForwardedEvent(true);
                attemptToIndex(id, retry);
              } catch (Exception e) {
                log.warn("{} {} could not be indexed", indexName(), id, e);
              }
            },
            retryInterval,
            TimeUnit.MILLISECONDS);
    return true;
  }

  public void scheduleIndexing(T id, Optional<E> event, Consumer<T> indexOnce) {
    IndexingRetry retry = new IndexingRetry(event, 0);
    if (indexingRetryTaskMap.put(id, retry) != null) {
      indexOnce.accept(id);
      log.error(
          "Skipping indexing because there is already a running task for the specified id. Index name: {}, task id: {}",
          indexName(),
          id);
      return;
    }
    attemptToIndex(id, retry);
  }

  public final void reindexAndCheckIsUpToDate(T id, UpToDateChecker<E> upToDateChecker) {
    reindex(id);
    IndexingRetry retry = indexingRetryTaskMap.get(id);
    if (retry == null) {
      log.warn("{} {} successfully indexed by different task", indexName(), id);
      return;
    }
    if (!upToDateChecker.isUpToDate(retry.getEvent())) {
      log.warn("{} {} is not up-to-date. Rescheduling", indexName(), id);
      retry.incrementRetryNumber();
      rescheduleIndex(id);
      return;
    }

    if (retry.getRetryNumber() > 0) {
      log.warn(
          "{} {} has been eventually indexed after {} attempt(s)",
          indexName(),
          id,
          retry.getRetryNumber());
    } else {
      log.debug("{} {} successfully indexed", indexName(), id);
    }
    indexingRetryTaskMap.remove(id);
  }

  public class IndexingRetry {
    private final Optional<E> event;
    private int retryNumber;

    public IndexingRetry(Optional<E> event, int retryNumber) {
      this.event = event;
      this.retryNumber = retryNumber;
    }

    public int getRetryNumber() {
      return retryNumber;
    }

    public Optional<E> getEvent() {
      return event;
    }

    public void incrementRetryNumber() {
      ++retryNumber;
    }
  }
}
