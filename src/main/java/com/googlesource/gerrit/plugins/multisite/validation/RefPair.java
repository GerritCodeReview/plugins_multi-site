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

package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.common.base.MoreObjects;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import org.eclipse.jgit.lib.Ref;

public class RefPair {
  public final Ref oldRef;
  public final Ref newRef;
  public final Exception exception;

  RefPair(Ref oldRef, Ref newRef) {
    this.oldRef = nullRefCleansing(oldRef);
    this.newRef = nullRefCleansing(newRef);
    this.exception = null;
  }

  private Ref nullRefCleansing(Ref ref) {
    return ref == null ? SharedRefDatabase.NULL_REF : ref;
  }

  RefPair(Ref newRef, Exception e) {
    this.newRef = newRef;
    this.oldRef = SharedRefDatabase.NULL_REF;
    this.exception = e;
  }

  public String getName() {
    return MoreObjects.firstNonNull(
        oldRef == null ? null : oldRef.getName(), newRef == null ? null : newRef.getName());
  }

  public boolean hasFailed() {
    return exception != null;
  }
}
