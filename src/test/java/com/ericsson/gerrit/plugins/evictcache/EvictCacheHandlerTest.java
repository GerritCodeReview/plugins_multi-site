// Copyright (C) 2015 Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ericsson.gerrit.plugins.evictcache;

import static org.easymock.EasyMock.expect;

import com.google.common.cache.RemovalNotification;

import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.concurrent.ScheduledThreadPoolExecutor;

@RunWith(PowerMockRunner.class)
@PrepareForTest(RemovalNotification.class)
public class EvictCacheHandlerTest extends EasyMockSupport {
  private static final String pluginName = "evict-cache";
  private static final String cacheName = "project_list";
  private static final String invalidCacheName = "INVALID";
  private static final boolean MOCK_REST_CLIENT = true;
  private static final boolean DO_NOT_MOCK_REST_CLIENT = false;
  private static final boolean WAS_EVICTED = true;
  private static final boolean WAS_NOT_EVICTED = false;

  private EvictCacheHandler<Object, Object> evictCacheHandler;
  private RemovalNotification<Object, Object> notification;
  private ScheduledThreadPoolExecutor pool;
  private RestSession restClient;

  @Test
  public void testEvictCacheHandler() {
    setUpMocks(MOCK_REST_CLIENT, WAS_NOT_EVICTED);
    EasyMock.replay(restClient);
    evictCacheHandler.onRemoval(pluginName, cacheName, notification);
    verifyAll();
  }

  @Test
  public void testInvalidCacheName() {
    setUpMocks(DO_NOT_MOCK_REST_CLIENT, WAS_NOT_EVICTED);
    replayAll();
    evictCacheHandler.onRemoval(pluginName, invalidCacheName, notification);
    verifyAll();
  }

  @Test
  public void testInvalidRemovalCause() {
    setUpMocks(DO_NOT_MOCK_REST_CLIENT, WAS_EVICTED);
    evictCacheHandler.onRemoval(pluginName, cacheName, notification);
    verifyAll();
  }

  @Test
  public void testInvalidRemovalCauseAndCacheName() {
    setUpMocks(DO_NOT_MOCK_REST_CLIENT, WAS_EVICTED);
    evictCacheHandler.onRemoval(pluginName, invalidCacheName, notification);
    verifyAll();
  }

  @Test
  public void testForwardedInvalidRemovalCauseAndCacheName() {
    setUpMocks(DO_NOT_MOCK_REST_CLIENT, WAS_EVICTED);
    Context.setForwardedEvent();
    try {
      evictCacheHandler.onRemoval(pluginName, invalidCacheName, notification);
    } finally {
      Context.unsetForwardedEvent();
    }
    verifyAll();
  }

  @Test
  public void testEvictCacheHandlerIsForwarded() {
    setUpMocks(DO_NOT_MOCK_REST_CLIENT, WAS_NOT_EVICTED);
    Context.setForwardedEvent();
    try {
      evictCacheHandler.onRemoval(pluginName, cacheName, notification);
    } finally {
      Context.unsetForwardedEvent();
    }
    verifyAll();
  }

  @Test
  public void testEvictCacheIsForwardedAndAlreadyEvicted() {
    setUpMocks(DO_NOT_MOCK_REST_CLIENT, WAS_EVICTED);
    Context.setForwardedEvent();
    try {
      evictCacheHandler.onRemoval(pluginName, cacheName, notification);
    } finally {
      Context.unsetForwardedEvent();
    }
    verifyAll();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void setUpMocks(boolean mockRestClient, boolean wasEvicted) {
    notification = PowerMock.createMock(RemovalNotification.class);
    pool = new PoolMock(1);
    Object key = new Object();
    if (mockRestClient) {
      restClient = createMock(RestSession.class);
      expect(restClient.evict(pluginName, cacheName, key)).andReturn(true);
    } else {
      restClient = null;
    }
    expect(notification.wasEvicted()).andReturn(wasEvicted);
    expect(notification.getKey()).andReturn(key);
    EasyMock.replay(notification);
    evictCacheHandler = new EvictCacheHandler(restClient, pool);
  }

  private class PoolMock extends ScheduledThreadPoolExecutor {
    PoolMock(int corePoolSize) {
      super(corePoolSize);
    }

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }
}
