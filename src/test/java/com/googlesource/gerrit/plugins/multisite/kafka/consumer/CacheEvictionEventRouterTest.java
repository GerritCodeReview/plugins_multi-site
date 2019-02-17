package com.googlesource.gerrit.plugins.multisite.kafka.consumer;

import static org.mockito.Mockito.verify;

import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.googlesource.gerrit.plugins.multisite.forwarder.CacheEntry;
import com.googlesource.gerrit.plugins.multisite.forwarder.ForwardedCacheEvictionHandler;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.CacheEvictionEvent;
import com.googlesource.gerrit.plugins.multisite.kafka.router.CacheEvictionEventRouter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CacheEvictionEventRouterTest {

  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private CacheEvictionEventRouter router;
  @Mock private ForwardedCacheEvictionHandler cacheEvictionHandler;

  @Before
  public void setUp() {
    router = new CacheEvictionEventRouter(cacheEvictionHandler);
  }

  @Test
  public void routerShouldSendEventsToTheAppropriateHandler_CacheEviction() throws Exception {
    final CacheEvictionEvent event = new CacheEvictionEvent("cache", "key");
    router.route(event);

    verify(cacheEvictionHandler).evict(CacheEntry.from(event.cacheName, event.key));
  }
}
