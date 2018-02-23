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

package com.ericsson.gerrit.plugins.highavailability.cache;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CachePattenMatcherTest {

  @Mock(answer = RETURNS_DEEP_STUBS)
  private Configuration configurationMock;

  @Test
  public void testCachePatternMatcher() throws Exception {
    when(configurationMock.cache().patterns()).thenReturn(ImmutableList.of("^my_cache.*", "other"));
    CachePatternMatcher matcher = new CachePatternMatcher(configurationMock);
    for (String cache :
        ImmutableList.of(
            "accounts_byemail",
            "ldap_groups",
            "project_list",
            "my_cache_a",
            "my_cache_b",
            "other")) {
      assertThat(matcher.matches(cache)).isTrue();
    }
    for (String cache : ImmutableList.of("ldap_groups_by_include", "foo")) {
      assertThat(matcher.matches(cache)).isFalse();
    }
  }
}
