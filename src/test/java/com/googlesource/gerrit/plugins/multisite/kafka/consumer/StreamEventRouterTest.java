package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import static org.mockito.Mockito.verify;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedEventHandler;
import com.googlesource.gerrit.plugins.multisite.kafka.router.StreamEventRouter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StreamEventRouterTest {

  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private StreamEventRouter router;
  @Mock private ForwardedEventHandler streamEventHandler;

  @Before
  public void setUp() {
    router = new StreamEventRouter(streamEventHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_StreamEvent() throws Exception {
    final CommentAddedEvent event = new CommentAddedEvent(aChange());
    router.route(event);
    verify(streamEventHandler).dispatch(event);
  }

  private Change aChange() {
    return new Change(
        new Change.Key("Iabcd1234abcd1234abcd1234abcd1234abcd1234"),
        new Change.Id(1),
        new Account.Id(1),
        new Branch.NameKey("proj", "refs/heads/master"),
        TimeUtil.nowTs());
  }
}
