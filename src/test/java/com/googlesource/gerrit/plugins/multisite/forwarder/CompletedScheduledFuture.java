// Copyright (C) 2024 The Android Open Source Project
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Ignore;

@Ignore
public class CompletedScheduledFuture<V> implements ScheduledFuture<V> {

  private final CompletableFuture<V> future;

  public CompletedScheduledFuture(CompletableFuture<V> future) {
    this.future = future;
  }

  @Override
  public long getDelay(TimeUnit unit) {
    return 0;
  }

  @Override
  public int compareTo(Delayed other) {
    return 0;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return future.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return future.isCancelled();
  }

  @Override
  public boolean isDone() {
    return future.isDone();
  }

  @Override
  public V get() throws InterruptedException, ExecutionException {
    return future.get();
  }

  @Override
  public V get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return future.get(timeout, unit);
  }
}
