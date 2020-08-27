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
  private static final long TEST_TIMEOUT_MSECS = 30000L;
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

  private void mockNewMessageResponse(Event event) {
    when(brokerApi.newMessage(any(), any()))
        .thenReturn(objectUnderTest.newMessage(instanceId, event));
  }

  @Before
  public void setUp() {
    Context.unsetForwardedEvent();
    objectUnderTest = broker(null);
    mockNewMessageResponse(event);
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
    TestEvent testEvent = new TestEvent("some-other-gerrit");
    mockNewMessageResponse(testEvent);

    objectUnderTest.send(topic, testEvent);
    verify(brokerApi, never()).send(any(), any());
  }

  @Test
  public void shouldNotDispatchEventWhenEventComesFromHighAvailabilityPluginThread()
      throws InterruptedException {
    TestEvent testEvent = new TestEvent(null);
    mockNewMessageResponse(testEvent);

    Thread t = new Thread(() -> objectUnderTest.send(topic, testEvent));
    t.setName("/plugins/high-availability/");
    t.start();
    t.join(TEST_TIMEOUT_MSECS);

    verify(brokerApi, never()).send(any(), any());
  }

  @Test
  public void shouldNotDispatchEventWhenEventComesFromHighAvailabilityForwarderThread()
      throws InterruptedException {
    TestEvent testEvent = new TestEvent(null);
    mockNewMessageResponse(testEvent);

    Thread t = new Thread(() -> objectUnderTest.send(topic, testEvent));
    t.setName("Forwarded-Index-Event");
    t.start();
    t.join(TEST_TIMEOUT_MSECS);

    verify(brokerApi, never()).send(any(), any());
  }

  @Test
  public void shouldDispatchEventWhenEventComesFromAnyThread() throws InterruptedException {
    TestEvent testEvent = new TestEvent(null);
    mockNewMessageResponse(testEvent);

    Thread t = new Thread(() -> objectUnderTest.send(topic, testEvent));
    t.start();
    t.join(TEST_TIMEOUT_MSECS);

    verify(brokerApi).send(any(), any());
  }

  @Test
  public void shouldNotDispatchEventWhenInstanceIdsAreDifferent() {
    TestEvent testEvent = new TestEvent("some-other-instance-id");

    objectUnderTest = broker("gerrit-instance-Id");
    mockNewMessageResponse(testEvent);

    objectUnderTest.send(topic, testEvent);
    verify(brokerApi, never()).send(any(), any());
  }

  @Test
  public void shouldDispatchEventWhenInstanceIdsAreEquals() {
    String gerritInstanceId = "gerrit-instance-Id";
    TestEvent testEvent = new TestEvent(gerritInstanceId);

    objectUnderTest = broker(gerritInstanceId);
    when(brokerApi.send(any(), any())).thenReturn(true);
    mockNewMessageResponse(testEvent);

    objectUnderTest.send(topic, testEvent);
    verify(brokerMetrics, only()).incrementBrokerPublishedMessage();
  }
}
