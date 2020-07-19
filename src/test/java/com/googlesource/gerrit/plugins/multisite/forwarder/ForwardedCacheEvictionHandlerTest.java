// Copyright (C) 2018 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.forwarder;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.googlesource.gerrit.plugins.multisite.cache.Constants;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedCacheEvictionHandlerTest {

  @Rule public ExpectedException exception = ExpectedException.none();
  @Mock private DynamicMap<Cache<?, ?>> cacheMapMock;
  private Cache<Object, Object> cacheUnderTest;
  private ForwardedCacheEvictionHandler handler;

  @Before
  public void setUp() throws Exception {
    handler = new ForwardedCacheEvictionHandler(cacheMapMock);
    cacheUnderTest = CacheBuilder.newBuilder().build();
  }

  @Test
  public void shouldThrowAnExceptionWhenCacheNotFound() throws Exception {
    CacheEntry entry = new CacheEntry("somePlugin", "unexistingCache", null);

    exception.expect(CacheNotFoundException.class);
    exception.expectMessage(
        String.format("cache %s.%s not found", entry.getPluginName(), entry.getCacheName()));
    handler.evict(entry);
  }

  @Test
  public void testSuccessfulCacheEviction() throws Exception {
    CacheEntry entry = new CacheEntry(Constants.GERRIT, Constants.ACCOUNTS, Account.id(123));
    cacheUnderTest.put(entry.getKey(), new Object());
    doReturn(cacheUnderTest).when(cacheMapMock).get(entry.getPluginName(), entry.getCacheName());

    handler.evict(entry);
    assertThat(cacheUnderTest.getIfPresent(entry.getKey())).isNull();
  }

  @Test
  public void testSuccessfulProjectListCacheEviction() throws Exception {
    CacheEntry entry = new CacheEntry(Constants.GERRIT, Constants.PROJECT_LIST, null);
    cacheUnderTest.put("foo", new Object());
    cacheUnderTest.put("bar", new Object());
    doReturn(cacheUnderTest).when(cacheMapMock).get(entry.getPluginName(), entry.getCacheName());

    handler.evict(entry);
    assertThat(cacheUnderTest.getIfPresent("foo")).isNull();
    assertThat(cacheUnderTest.getIfPresent("bar")).isNull();
  }

  @Test
  public void shouldSetAndUnsetForwardedContext() throws Exception {
    CacheEntry entry = new CacheEntry(Constants.GERRIT, Constants.ACCOUNTS, Account.id(456));
    doReturn(cacheUnderTest).when(cacheMapMock).get(entry.getPluginName(), entry.getCacheName());

    cacheUnderTest =
        new Cache<Object, Object>() {

          @Override
          public Object getIfPresent(Object key) {
            return null;
          }

          @Override
          public Object get(Object key, Callable<? extends Object> loader)
              throws ExecutionException {
            return null;
          }

          @Override
          public ImmutableMap<Object, Object> getAllPresent(Iterable<?> keys) {
            return null;
          }

          @Override
          public void put(Object key, Object value) {}

          @Override
          public void putAll(Map<? extends Object, ? extends Object> m) {}

          @Override
          public void invalidate(Object key) {
            assertThat(Context.isForwardedEvent()).isTrue();
          }

          @Override
          public void invalidateAll(Iterable<?> keys) {}

          @Override
          public void invalidateAll() {}

          @Override
          public long size() {
            return 0;
          }

          @Override
          public CacheStats stats() {
            return null;
          }

          @Override
          public ConcurrentMap<Object, Object> asMap() {
            return null;
          }

          @Override
          public void cleanUp() {}
        };

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.evict(entry);
    assertThat(Context.isForwardedEvent()).isFalse();
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrown() throws Exception {
    CacheEntry entry = new CacheEntry(Constants.GERRIT, Constants.ACCOUNTS, Account.id(789));
    cacheUnderTest =
        new Cache<Object, Object>() {

          @Override
          public Object getIfPresent(Object key) {
            return null;
          }

          @Override
          public Object get(Object key, Callable<? extends Object> loader)
              throws ExecutionException {
            return null;
          }

          @Override
          public ImmutableMap<Object, Object> getAllPresent(Iterable<?> keys) {
            return null;
          }

          @Override
          public void put(Object key, Object value) {}

          @Override
          public void putAll(Map<? extends Object, ? extends Object> m) {}

          @Override
          public void invalidate(Object key) {
            assertThat(Context.isForwardedEvent()).isTrue();
            throw new RuntimeException();
          }

          @Override
          public void invalidateAll(Iterable<?> keys) {}

          @Override
          public void invalidateAll() {}

          @Override
          public long size() {
            return 0;
          }

          @Override
          public CacheStats stats() {
            return null;
          }

          @Override
          public ConcurrentMap<Object, Object> asMap() {
            return null;
          }

          @Override
          public void cleanUp() {}
        };
    doReturn(cacheUnderTest).when(cacheMapMock).get(entry.getPluginName(), entry.getCacheName());

    assertThat(Context.isForwardedEvent()).isFalse();
    try {
      handler.evict(entry);
    } catch (RuntimeException e) {
    }
    assertThat(Context.isForwardedEvent()).isFalse();
  }
}
