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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.KafkaConfiguration;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerGson;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.StreamEventRouter;
import java.util.UUID;
import org.apache.kafka.common.serialization.Deserializer;

@Singleton
public class StreamEventSubscriber extends AbstractKafkaSubcriber {
  @Inject
  public StreamEventSubscriber(
      KafkaConfiguration configuration,
      Deserializer<byte[]> keyDeserializer,
      Deserializer<SourceAwareEventWrapper> valueDeserializer,
      StreamEventRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      @BrokerGson Gson gson,
      @InstanceId UUID instanceId,
      OneOffRequestContext oneOffCtx,
      MessageLogger msgLog) {
    super(
        configuration,
        keyDeserializer,
        valueDeserializer,
        eventRouter,
        droppedEventListeners,
        gson,
        instanceId,
        oneOffCtx,
        msgLog);
  }

  @Override
  protected EventFamily getEventFamily() {
    return EventFamily.STREAM_EVENT;
  }
}
