package com.googlesource.gerrit.plugins.multisite.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gerritforge.gerrit.globalrefdb.validation.ProjectsFilter;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.Configuration.Broker;
import com.googlesource.gerrit.plugins.multisite.MessageLogger;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheNotFoundException;
import com.googlesource.gerrit.plugins.multisite.forwarder.router.ForwardedEventRouter;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@Ignore
public abstract class AbstractSubscriberTestBase {
  protected static final String NODE_INSTANCE_ID = "node-instance-id";
  protected static final String INSTANCE_ID = "other-node-instance-id";
  protected static final String PROJECT_NAME = "project-name";

  @Mock protected DroppedEventListener droppedEventListeners;
  @Mock protected MessageLogger msgLog;
  @Mock protected SubscriberMetrics subscriberMetrics;
  @Mock protected Configuration cfg;
  @Mock protected Broker brokerCfg;
  @Mock protected ProjectsFilter projectsFilter;

  @SuppressWarnings("rawtypes")
  protected ForwardedEventRouter eventRouter;

  protected AbstractSubcriber objectUnderTest;

  @Before
  public void setup() {
    when(cfg.broker()).thenReturn(brokerCfg);
    when(brokerCfg.getTopic(any(), any())).thenReturn("test-topic");
    eventRouter = eventRouter();
    objectUnderTest = objectUnderTest();
  }

  @Test
  public void shouldConsumeEventsWhenNotFilteredByProjectName()
      throws IOException, PermissionBackendException, CacheNotFoundException {
    for (Event event : events()) {
      when(projectsFilter.matches(any(String.class))).thenReturn(true);
      objectUnderTest.getConsumer().accept(event);
      verifyConsumed(event);
    }
  }

  @Test
  public void shouldSkipEventsWhenFilteredByProjectName()
      throws IOException, PermissionBackendException, CacheNotFoundException {
    for (Event event : events()) {
      when(projectsFilter.matches(any(String.class))).thenReturn(false);
      objectUnderTest.getConsumer().accept(event);
      verifySkipped(event);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldSkipLocalEvents()
      throws IOException, PermissionBackendException, CacheNotFoundException {
    for (Event event : events()) {
      event.instanceId = NODE_INSTANCE_ID;
      when(projectsFilter.matches(any(String.class))).thenReturn(true);

      objectUnderTest.getConsumer().accept(event);

      verify(projectsFilter, never()).matches(PROJECT_NAME);
      verify(eventRouter, never()).route(event);
      verify(droppedEventListeners, times(1)).onEventDropped(event);
      reset(projectsFilter, eventRouter, droppedEventListeners);
    }
  }

  protected abstract AbstractSubcriber objectUnderTest();

  protected abstract List<Event> events();

  @SuppressWarnings("rawtypes")
  protected abstract ForwardedEventRouter eventRouter();

  @SuppressWarnings("unchecked")
  protected void verifySkipped(Event event)
      throws IOException, PermissionBackendException, CacheNotFoundException {
    verify(projectsFilter, times(1)).matches(PROJECT_NAME);
    verify(eventRouter, never()).route(event);
    verify(droppedEventListeners, times(1)).onEventDropped(event);
    reset(projectsFilter, eventRouter, droppedEventListeners);
  }

  @SuppressWarnings("unchecked")
  protected void verifyConsumed(Event event)
      throws IOException, PermissionBackendException, CacheNotFoundException {
    verify(projectsFilter, times(1)).matches(PROJECT_NAME);
    verify(eventRouter, times(1)).route(event);
    verify(droppedEventListeners, never()).onEventDropped(event);
    reset(projectsFilter, eventRouter, droppedEventListeners);
  }

  protected DynamicSet<DroppedEventListener> asDynamicSet(DroppedEventListener listener) {
    DynamicSet<DroppedEventListener> result = new DynamicSet<>();
    result.add("multi-site", listener);
    return result;
  }
}
