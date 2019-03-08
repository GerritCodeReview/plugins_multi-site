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

package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import static com.googlesource.gerrit.plugins.multisite.MultiSiteLogFile.multisiteLog;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.Executor;

@Singleton
public class MultiSiteKafkaConsumerRunner implements LifecycleListener {
  private final DynamicSet<AbstractKafkaSubcriber> consumers;
  private final Executor executor;

  @Inject
  public MultiSiteKafkaConsumerRunner(
      @ConsumerExecutor Executor executor, DynamicSet<AbstractKafkaSubcriber> consumers) {
    this.consumers = consumers;
    this.executor = executor;
  }

  @Override
  public void start() {
    multisiteLog.info("starting consumers");
    consumers.forEach(c -> executor.execute(c));
  }

  @Override
  public void stop() {
    multisiteLog.info("shutting down consumers");
    this.consumers.forEach(c -> c.shutdown());
  }
}
