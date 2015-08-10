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

package com.ericsson.gerrit.plugins.syncindex;

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.expect;

import org.easymock.EasyMockSupport;
import org.junit.Test;

public class ModuleTest extends EasyMockSupport {

  @Test
  public void testSyncUrlProvider() {
    Configuration configMock = createNiceMock(Configuration.class);
    String expected = "someUrl";
    expect(configMock.getUrl()).andReturn(expected);
    replayAll();
    Module module = new Module();
    assertThat(module.syncUrl(configMock)).isEqualTo(expected);
  }
}
