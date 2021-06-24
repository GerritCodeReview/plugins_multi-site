package com.googlesource.gerrit.plugins.multisite.broker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectCreatedEvent;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BrokerApiWrapperTest {
  private static final String DEFAULT_INSTANCE_ID = "instance-id";
  @Mock private BrokerMetrics brokerMetrics;
  @Mock private BrokerApi brokerApi;
  @Mock Event event;
  @Mock MessageLogger msgLog;
  private String topic = "index";

  private BrokerApiWrapper objectUnderTest;

  @Before
  public void setUp() {
    event.instanceId = DEFAULT_INSTANCE_ID;
    objectUnderTest =
        new BrokerApiWrapper(
            MoreExecutors.directExecutor(),
            DynamicItem.itemOf(BrokerApi.class, brokerApi),
            brokerMetrics,
            msgLog,
            DEFAULT_INSTANCE_ID);
  }

  @Test
  public void shouldIncrementBrokerMetricCounterWhenMessagePublished() {
    SettableFuture<Boolean> resultF = SettableFuture.create();
    resultF.set(true);
    when(brokerApi.send(any(), any())).thenReturn(resultF);
    objectUnderTest.send(topic, event);
    verify(brokerMetrics, only()).incrementBrokerPublishedMessage();
  }

  @Test
  public void shouldIncrementBrokerFailedMetricCounterWhenMessagePublishingFailed() {
    SettableFuture<Boolean> resultF = SettableFuture.create();
    resultF.setException(new Exception("Force Future failure"));
    when(brokerApi.send(any(), any())).thenReturn(resultF);
    objectUnderTest.send(topic, event);
    verify(brokerMetrics, only()).incrementBrokerFailedToPublishMessage();
  }

  @Test
  public void shouldIncrementBrokerFailedMetricCounterWhenUnexpectedException() {
    when(brokerApi.send(any(), any()))
        .thenThrow(new RuntimeException("Unexpected runtime exception"));
    try {
      objectUnderTest.sendSync(topic, event);
    } catch (RuntimeException e) {
      // expected
    }
    verify(brokerMetrics, only()).incrementBrokerFailedToPublishMessage();
  }

  @Test
  public void shouldSkipMessageSendingWhenInstanceIdIsNull() {
    ProjectCreatedEvent event = new ProjectCreatedEvent();
    event.instanceId = null;
    objectUnderTest.send(topic, event);
    verify(brokerApi, never()).send(any(), eq(event));
  }

  @Test
  public void shouldSkipMessageSendingWhenInstanceIdIsEmpty() {
    ProjectCreatedEvent event = new ProjectCreatedEvent();
    event.instanceId = "";
    objectUnderTest.send(topic, event);
    verify(brokerApi, never()).send(any(), eq(event));
  }
}
