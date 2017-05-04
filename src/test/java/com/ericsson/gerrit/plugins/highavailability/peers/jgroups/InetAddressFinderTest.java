// Copyright (C) 2017 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.peers.jgroups;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InetAddressFinderTest {

  @Mock private Configuration configuration;
  @Mock private Configuration.PeerInfoJGroups jgroupsConfig;
  private InetAddressFinder finder;

  @Before
  public void setUp() {
    when(configuration.peerInfoJGroups()).thenReturn(jgroupsConfig);
    finder = new InetAddressFinder(configuration);
  }

  @Test
  public void testNoSkipWhenEmptySkipList() {
    when(jgroupsConfig.skipInterface()).thenReturn(ImmutableList.<String>of());
    assertThat(finder.shouldSkip("foo")).isFalse();
    assertThat(finder.shouldSkip("bar")).isFalse();
  }

  @Test
  public void testSkipByName() {
    when(jgroupsConfig.skipInterface()).thenReturn(ImmutableList.of("foo"));
    assertThat(finder.shouldSkip("foo")).isTrue();
    assertThat(finder.shouldSkip("bar")).isFalse();

    when(jgroupsConfig.skipInterface()).thenReturn(ImmutableList.of("foo", "bar"));
    assertThat(finder.shouldSkip("foo")).isTrue();
    assertThat(finder.shouldSkip("bar")).isTrue();
  }

  @Test
  public void testSkipByWildcard() {
    when(jgroupsConfig.skipInterface()).thenReturn(ImmutableList.of("foo*"));
    assertThat(finder.shouldSkip("foo")).isTrue();
    assertThat(finder.shouldSkip("foo1")).isTrue();
    assertThat(finder.shouldSkip("bar")).isFalse();
  }
}
