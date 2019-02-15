package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEntry;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedCacheEvictionHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedEventHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexAccountHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexChangeHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexGroupHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexProjectHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedIndexingHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.AccountIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.GroupIndexEvent;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ProjectIndexEvent;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedEventRouterTest {

  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private ForwardedEventRouter router;
  @Mock private ForwardedCacheEvictionHandler cacheEvictionHandler;
  @Mock private ForwardedIndexAccountHandler indexAccountHandler;
  @Mock private ForwardedIndexChangeHandler indexChangeHandler;
  @Mock private ForwardedIndexGroupHandler indexGroupHandler;
  @Mock private ForwardedIndexProjectHandler indexProjectHandler;
  @Mock private ForwardedEventHandler streamEventHandler;

  @Before
  public void setUp() {
    router =
        new ForwardedEventRouter(
            cacheEvictionHandler,
            indexAccountHandler,
            indexChangeHandler,
            indexGroupHandler,
            indexProjectHandler,
            streamEventHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_CacheEviction() throws Exception {
    final CacheEvictionEvent event = new CacheEvictionEvent("cache", "key");
    router.route(event);

    verify(cacheEvictionHandler).evict(CacheEntry.from(event.cacheName, event.key));

    verifyZeroInteractions(
        indexAccountHandler,
        indexChangeHandler,
        indexGroupHandler,
        indexProjectHandler,
        streamEventHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_AccountIndex() throws Exception {
    final AccountIndexEvent event = new AccountIndexEvent(1);
    router.route(event);

    verify(indexAccountHandler)
        .index(
            new Account.Id(event.accountId),
            ForwardedIndexingHandler.Operation.INDEX,
            Optional.of(event));

    verifyZeroInteractions(
        indexChangeHandler,
        indexGroupHandler,
        indexProjectHandler,
        streamEventHandler,
        cacheEvictionHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_GroupIndex() throws Exception {
    final String groupId = "12";
    final GroupIndexEvent event = new GroupIndexEvent(groupId);
    router.route(event);

    verify(indexGroupHandler)
        .index(
            new AccountGroup.UUID(groupId),
            ForwardedIndexingHandler.Operation.INDEX,
            Optional.of(event));

    verifyZeroInteractions(
        indexAccountHandler,
        indexChangeHandler,
        indexProjectHandler,
        streamEventHandler,
        cacheEvictionHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ProjectIndex() throws Exception {
    final String projectName = "projectName";
    final ProjectIndexEvent event = new ProjectIndexEvent(projectName);
    router.route(event);

    verify(indexProjectHandler)
        .index(
            Project.NameKey.parse(projectName),
            ForwardedIndexingHandler.Operation.INDEX,
            Optional.of(event));

    verifyZeroInteractions(
        indexAccountHandler,
        indexChangeHandler,
        indexGroupHandler,
        streamEventHandler,
        cacheEvictionHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ChangeIndex() throws Exception {
    final ChangeIndexEvent event = new ChangeIndexEvent("projectName", 3, false);
    router.route(event);

    verify(indexChangeHandler)
        .index(
            event.projectName + "~" + event.changeId,
            ForwardedIndexingHandler.Operation.INDEX,
            Optional.of(event));

    verifyZeroInteractions(
        indexAccountHandler,
        indexGroupHandler,
        indexProjectHandler,
        streamEventHandler,
        cacheEvictionHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_ChangeIndexDelete() throws Exception {
    final ChangeIndexEvent event = new ChangeIndexEvent("projectName", 3, true);
    router.route(event);

    verify(indexChangeHandler)
        .index(
            event.projectName + "~" + event.changeId,
            ForwardedIndexingHandler.Operation.DELETE,
            Optional.of(event));

    verifyZeroInteractions(
        indexAccountHandler,
        indexGroupHandler,
        indexProjectHandler,
        streamEventHandler,
        cacheEvictionHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_StreamEvent() throws Exception {
    final CommentAddedEvent event = new CommentAddedEvent(aChange());
    router.route(event);
    verify(streamEventHandler).dispatch(event);
    verifyZeroInteractions(
        indexAccountHandler,
        indexChangeHandler,
        indexGroupHandler,
        indexProjectHandler,
        cacheEvictionHandler);
  }

  @Test
  public void routerShouldFailForNotRecognisedEvents() throws Exception {
    final Event newEventType = new Event("new-type") {};

    try {
      router.route(newEventType);
      Assert.fail("Expected exception for not supported event");
    } catch (UnsupportedOperationException expected) {
      verifyZeroInteractions(
          indexAccountHandler,
          indexChangeHandler,
          indexGroupHandler,
          indexProjectHandler,
          streamEventHandler,
          cacheEvictionHandler);
    }
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
