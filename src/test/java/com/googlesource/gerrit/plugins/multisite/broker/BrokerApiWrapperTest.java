package com.googlesource.gerrit.plugins.multisite.broker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.eventbroker.BrokerApi;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.events.Event;
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
  private String topic = "index";

  private BrokerApiWrapper objectUnderTest;

  @Before
  public void setUp() {
    objectUnderTest =
        new BrokerApiWrapper(DynamicItem.itemOf(BrokerApi.class, brokerApi), brokerMetrics);
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
}
