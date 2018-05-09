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

package com.ericsson.gerrit.plugins.highavailability.index;

public class GroupIndexForwardingIT extends AbstractIndexForwardingIT {
  private String someGroupId;

  @Override
  public void beforeAction() throws Exception {
    someGroupId = gApi.groups().create("someGroup").get().id;
  }

  @Override
  public String getExpectedRequest() {
    return "/plugins/high-availability/index/group/" + someGroupId;
  }

  @Override
  public void doAction() throws Exception {
    gApi.groups().id(someGroupId).index();
  }
}
