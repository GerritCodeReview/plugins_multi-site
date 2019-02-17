package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import static org.mockito.Mockito.verify;

import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedProjectListUpdateHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectListUpdateEvent;
import com.googlesource.gerrit.plugins.multisite.kafka.router.ProjectListUpdateRouter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectListUpdateRouterTest {

  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private ProjectListUpdateRouter router;
  @Mock private ForwardedProjectListUpdateHandler projectListUpdateHandler;

  @Before
  public void setUp() {
    router = new ProjectListUpdateRouter(projectListUpdateHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ProjectListUpdate() throws Exception {
    final ProjectListUpdateEvent event = new ProjectListUpdateEvent("project", false);
    router.route(event);

    verify(projectListUpdateHandler).update(event);
  }
}
