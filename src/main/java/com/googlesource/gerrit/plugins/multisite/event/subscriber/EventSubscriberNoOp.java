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

package com.googlesource.gerrit.plugins.multisite.event.subscriber;

import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.kafka.consumer.SourceAwareEventWrapper;
import java.util.function.Consumer;

@Singleton
public class EventSubscriberNoOp implements EventSubscriber {

  @Override
  public void subscribe(
      EventFamily eventFamily, Consumer<SourceAwareEventWrapper> messageProcessor) {}

  @Override
  public void shutdown() {}
}
