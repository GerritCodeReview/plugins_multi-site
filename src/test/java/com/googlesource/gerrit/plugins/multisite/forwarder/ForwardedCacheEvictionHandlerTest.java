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
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.googlesource.gerrit.plugins.multisite.cache.Constants;
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
}
