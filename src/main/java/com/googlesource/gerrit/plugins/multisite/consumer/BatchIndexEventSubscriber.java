package com.googlesource.gerrit.plugins.multisite.consumer;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.multisite.InstanceId;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerApiWrapper;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerGson;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventTopic;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.IndexEventRouter;
import java.util.UUID;

@Singleton
public class BatchIndexEventSubscriber extends AbstractSubcriber {
  @Inject
  public BatchIndexEventSubscriber(
      BrokerApiWrapper brokerApi,
      IndexEventRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      @BrokerGson Gson gsonProvider,
      @InstanceId UUID instanceId,
      MessageLogger msgLog,
      SubscriberMetrics subscriberMetrics) {
    super(
        brokerApi,
        eventRouter,
        droppedEventListeners,
        gsonProvider,
        instanceId,
        msgLog,
        subscriberMetrics);
  }

  @Override
  protected EventTopic getTopic() {
    return EventTopic.BATCH_INDEX_TOPIC;
  }
}
