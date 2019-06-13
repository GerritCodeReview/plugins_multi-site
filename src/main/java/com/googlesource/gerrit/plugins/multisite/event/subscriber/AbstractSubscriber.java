package com.googlesource.gerrit.plugins.multisite.event.subscriber;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gson.Gson;
import com.google.gwtorm.server.OrmException;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.MessageLogger.Direction;
import com.googlesource.gerrit.plugins.multisite.broker.BrokerGson;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.EventFamily;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import com.googlesource.gerrit.plugins.multisite.kafka.consumer.DroppedEventListener;
import com.googlesource.gerrit.plugins.multisite.kafka.consumer.SourceAwareEventWrapper;
import java.io.IOException;
import java.util.UUID;

public abstract class AbstractSubscriber implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final ForwardedEventRouter eventRouter;
  final DynamicSet<DroppedEventListener> droppedEventListeners;
  final UUID instanceId;
  final MessageLogger msgLog;
  final EventSubscriber consumer;
  private final Gson gson;

  public AbstractSubscriber(
      ForwardedEventRouter eventRouter,
      DynamicSet<DroppedEventListener> droppedEventListeners,
      UUID instanceId,
      MessageLogger msgLog,
      @BrokerGson Gson gson,
      EventSubscriber consumer) {
    this.eventRouter = eventRouter;
    this.droppedEventListeners = droppedEventListeners;
    this.instanceId = instanceId;
    this.msgLog = msgLog;
    this.gson = gson;
    this.consumer = consumer;
  }

  protected abstract EventFamily getEventFamily();

  @Override
  public void run() {
    consumer.subscribe(getEventFamily(), this::processRecord);
  }

  protected void processRecord(SourceAwareEventWrapper event) {

    if (event.getHeader().getSourceInstanceId().equals(instanceId)) {
      logger.atFiner().log(
          "Dropping event %s produced by our instanceId %s",
          event.toString(), instanceId.toString());
      droppedEventListeners.forEach(l -> l.onEventDropped(event));
    } else {
      try {
        msgLog.log(Direction.CONSUME, event);
        eventRouter.route(event.getEventBody(gson));
      } catch (IOException e) {
        logger.atSevere().withCause(e).log(
            "Malformed event '%s': [Exception: %s]", event.getHeader().getEventType());
      } catch (PermissionBackendException | OrmException | CacheNotFoundException e) {
        logger.atSevere().withCause(e).log(
            "Cannot handle message %s: [Exception: %s]", event.getHeader().getEventType());
      }
    }
  }

  public void shutdown() {
    consumer.shutdown();
  }
}
