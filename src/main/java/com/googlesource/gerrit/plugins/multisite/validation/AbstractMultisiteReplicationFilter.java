// Copyright (C) 2024 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.validation;

import static com.googlesource.gerrit.plugins.multisite.validation.ProjectVersionRefUpdate.MULTI_SITE_VERSIONING_REF;

abstract class AbstractMultisiteReplicationFilter {

  /*
   * Since ac43a5f94c773c9db7a73d44035961d69d13fa53 the 'refs/multi-site/version' is
   * not updated anymore on the global-refdb; however, the values stored already
   * on the global-refdb could get in the way and prevent replication from happening
   * as expected.
   *
   * Exclude the 'refs/multi-site/version' from local vs. global refdb checking
   * pretending that the global-refdb for that ref did not exist.
   */
  protected boolean shouldNotBeTrackedAnymoreOnGlobalRefDb(String ref) {
    return MULTI_SITE_VERSIONING_REF.equals(ref);
  }
}
