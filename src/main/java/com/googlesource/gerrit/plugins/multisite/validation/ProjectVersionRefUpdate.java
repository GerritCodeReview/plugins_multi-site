// Copyright (C) 2022 The Android Open Source Project
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

import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;

public interface ProjectVersionRefUpdate {

  String MULTI_SITE_VERSIONING_REF = "refs/multi-site/version";
  String MULTI_SITE_VERSIONING_VALUE_REF = "refs/multi-site/version/value";
  Ref NULL_PROJECT_VERSION_REF =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, MULTI_SITE_VERSIONING_REF, ObjectId.zeroId());

  Optional<Long> getProjectLocalVersion(String projectName);

  Optional<Long> getProjectRemoteVersion(String projectName);
}
