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

import com.google.gerrit.acceptance.TestAccount;

public class AccountIndexForwardingIT extends AbstractIndexForwardingIT {
  private TestAccount testAccount;

  @Override
  public void setup() throws Exception {
    testAccount = accounts.create("someUser");
  }

  @Override
  public String getExpectedRequest() {
    return "/plugins/high-availability/index/account/" + testAccount.id;
  }

  @Override
  public void doAction() throws Exception {
    gApi.accounts().id(testAccount.id.get()).setActive(false);
  }
}
