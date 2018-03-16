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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.reviewdb.client.Account;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedCacheEvictionHandlerTest {

  @Rule public ExpectedException exception = ExpectedException.none();
  @Mock private DynamicMap<Cache<?, ?>> cacheMapMock;
  @Mock private Cache<?, ?> cacheMock;
  private ForwardedCacheEvictionHandler handler;

  @Before
  public void setUp() throws Exception {
    handler = new ForwardedCacheEvictionHandler(cacheMapMock);
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
    CacheEntry entry = new CacheEntry(Constants.GERRIT, Constants.ACCOUNTS, new Account.Id(123));
    doReturn(cacheMock).when(cacheMapMock).get(entry.getPluginName(), entry.getCacheName());

    handler.evict(entry);
    verify(cacheMock).invalidate(entry.getKey());
  }

  @Test
  public void testSuccessfulProjectListCacheEviction() throws Exception {
    CacheEntry entry = new CacheEntry(Constants.GERRIT, Constants.PROJECT_LIST, null);
    doReturn(cacheMock).when(cacheMapMock).get(entry.getPluginName(), entry.getCacheName());

    handler.evict(entry);
    verify(cacheMock).invalidateAll();
  }

  @Test
  public void shouldSetAndUnsetForwardedContext() throws Exception {
    CacheEntry entry = new CacheEntry(Constants.GERRIT, Constants.ACCOUNTS, new Account.Id(456));
    doReturn(cacheMock).when(cacheMapMock).get(entry.getPluginName(), entry.getCacheName());

    //this doAnswer is to allow to assert that context is set to forwarded
    //while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(cacheMock)
        .invalidate(entry.getKey());

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.evict(entry);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(cacheMock).invalidate(entry.getKey());
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrown() throws Exception {
    CacheEntry entry = new CacheEntry(Constants.GERRIT, Constants.ACCOUNTS, new Account.Id(789));
    doReturn(cacheMock).when(cacheMapMock).get(entry.getPluginName(), entry.getCacheName());

    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new RuntimeException();
                })
        .when(cacheMock)
        .invalidate(entry.getKey());

    assertThat(Context.isForwardedEvent()).isFalse();
    try {
      handler.evict(entry);
    } catch (RuntimeException e) {
    }
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(cacheMock).invalidate(entry.getKey());
  }
}
