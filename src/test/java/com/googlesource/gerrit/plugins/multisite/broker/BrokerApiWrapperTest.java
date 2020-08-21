package com.googlesource.gerrit.plugins.multisite.broker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.events.Event;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.forwarder.Context;
import com.googlesource.gerrit.plugins.multisite.util.TestEvent;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BrokerApiWrapperTest {
  @Mock private BrokerMetrics brokerMetrics;
  @Mock private BrokerApi brokerApi;
  @Mock Event event;
  @Mock MessageLogger msgLog;
  private UUID instanceId = UUID.randomUUID();
  private String topic = "index";

  private BrokerApiWrapper objectUnderTest;

  private BrokerApiWrapper broker(@Nullable String gerritInstanceId) {
    return new BrokerApiWrapper(
        DynamicItem.itemOf(BrokerApi.class, brokerApi),
        brokerMetrics,
        msgLog,
        instanceId,
        gerritInstanceId);
  }

  @Before
  public void setUp() {
    Context.unsetForwardedEvent();
    objectUnderTest = broker(null);
    when(brokerApi.newMessage(any(), any()))
        .thenReturn(objectUnderTest.newMessage(instanceId, event));
  }

  @Test
  public void shouldIncrementBrokerMetricCounterWhenMessagePublished() {
    when(brokerApi.send(any(), any())).thenReturn(true);
    objectUnderTest.send(topic, event);
    verify(brokerMetrics, only()).incrementBrokerPublishedMessage();
  }

  @Test
  public void shouldIncrementBrokerFailedMetricCounterWhenMessagePublishingFailed() {
    when(brokerApi.send(any(), any())).thenReturn(false);
    objectUnderTest.send(topic, event);
    verify(brokerMetrics, only()).incrementBrokerFailedToPublishMessage();
  }

  @Test
  public void shouldIncrementBrokerFailedMetricCounterWhenUnexpectedException() {
    when(brokerApi.send(any(), any()))
        .thenThrow(new RuntimeException("Unexpected runtime exception"));
    try {
      objectUnderTest.send(topic, event);
    } catch (RuntimeException e) {
      // expected
    }
    verify(brokerMetrics, only()).incrementBrokerFailedToPublishMessage();
  }

  @Test
  public void shouldNotDispatchEventWhenEventInstanceIdIsDefinedButGerritInstanceIdIsNot() {
    TestEvent testEvent = new TestEvent();
    testEvent.setInstanceId("some-other-gerrit");
    when(brokerApi.newMessage(any(), any()))
        .thenReturn(objectUnderTest.newMessage(instanceId, testEvent));

    objectUnderTest.send(topic, testEvent);
    verify(brokerApi, never()).send(any(), any());
  }

  @Test
  public void shouldNotDispatchEventWhenGerritInstanceIdIsDefinedButEventInstanceIdIsNot() {
    TestEvent testEvent = new TestEvent();
    testEvent.setInstanceId(null);
    String gerritInstanceId = "gerrit-instance-Id";

    objectUnderTest = broker(gerritInstanceId);
    when(brokerApi.newMessage(any(), any()))
        .thenReturn(objectUnderTest.newMessage(instanceId, testEvent));

    objectUnderTest.send(topic, testEvent);
    verify(brokerApi, never()).send(any(), any());
  }

  @Test
  public void shouldNotDispatchEventWhenInstanceIdsAreDifferent() {
    String gerritInstanceId = "gerrit-instance-Id";
    TestEvent testEvent = new TestEvent();
    testEvent.setInstanceId("some-other-instance-id");

    objectUnderTest = broker(gerritInstanceId);
    when(brokerApi.newMessage(any(), any()))
        .thenReturn(objectUnderTest.newMessage(instanceId, testEvent));

    objectUnderTest.send(topic, testEvent);
    verify(brokerApi, never()).send(any(), any());
  }

  @Test
  public void shouldDispatchEventWhenInstanceIdsAreEquals() {
    String gerritInstanceId = "gerrit-instance-Id";
    TestEvent testEvent = new TestEvent();
    testEvent.setInstanceId(gerritInstanceId);

    objectUnderTest = broker(gerritInstanceId);
    when(brokerApi.send(any(), any())).thenReturn(true);
    when(brokerApi.newMessage(any(), any()))
        .thenReturn(objectUnderTest.newMessage(instanceId, testEvent));

    objectUnderTest.send(topic, testEvent);
    verify(brokerMetrics, only()).incrementBrokerPublishedMessage();
  }
}
