// Copyright (C) 2019 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import java.io.IOException;
import org.eclipse.jgit.lib.Ref;

/** Local project/ref is out of sync with the shared refdb */
public class OutOfSyncException extends IOException {
  private static final long serialVersionUID = 1L;

  public OutOfSyncException(String project, Ref localRef) {
    super(
        localRef == null
            ? String.format(
                "Local ref doesn't exists locally for project %s but exists in the shared ref-db",
                project)
            : String.format(
                "Local ref %s (ObjectId=%s) on project %s is out of sync with the shared ref-db",
                localRef.getName(), localRef.getObjectId().getName(), project));
  }
}
