package com.googlesource.gerrit.plugins.multisite.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@Ignore
public abstract class AbstractSubscriberTestBase {

  @Mock protected DroppedEventListener droppedEventListeners;
  @Mock protected MessageLogger msgLog;
  @Mock protected SubscriberMetrics subscriberMetrics;
  @Mock protected Configuration cfg;
  @Mock protected Broker brokerCfg;
  @Mock protected ProjectsFilter projectsFilter;

  @SuppressWarnings("rawtypes")
  protected ForwardedEventRouter eventRouter;

  protected static final String INSTANCE_ID = "node-instance-id";
  protected static final String PROJECT_NAME = "project-name";

  @Before
  public void setup() {
    when(cfg.broker()).thenReturn(brokerCfg);
    when(brokerCfg.getTopic(any(), any())).thenReturn("test-topic");
    eventRouter = eventRouter();
  }

  @SuppressWarnings("unchecked")
  protected void verifySkipped(Event event)
      throws IOException, PermissionBackendException, CacheNotFoundException {
    verify(projectsFilter, times(1)).matches(PROJECT_NAME);
    verify(eventRouter, never()).route(event);
    verify(droppedEventListeners, times(1)).onEventDropped(event);
  }

  @SuppressWarnings("unchecked")
  protected void verifyConsumed(Event event)
      throws IOException, PermissionBackendException, CacheNotFoundException {
    verify(projectsFilter, times(1)).matches(PROJECT_NAME);
    verify(eventRouter, times(1)).route(event);
    verify(droppedEventListeners, never()).onEventDropped(event);
  }

  @SuppressWarnings("rawtypes")
  protected abstract ForwardedEventRouter eventRouter();

  protected DynamicSet<DroppedEventListener> asDynamicSet(DroppedEventListener listener) {
    DynamicSet<DroppedEventListener> result = new DynamicSet<>();
    result.add("multi-site", listener);
    return result;
  }
}
