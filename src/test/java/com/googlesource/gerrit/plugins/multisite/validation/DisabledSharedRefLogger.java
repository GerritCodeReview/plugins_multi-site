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

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefLogger;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.junit.Ignore;

@Ignore
public class DisabledSharedRefLogger implements SharedRefLogger {

  @Override
  public void logRefUpdate(String project, Ref currRef, ObjectId newRefValue) {}

  @Override
  public void logProjectDelete(String project) {}

  @Override
  public void logLockAcquisition(String project, String refName) {}

  @Override
  public void logLockRelease(String project, String refName) {}

  @Override
  public <T> void logRefUpdate(String project, String refName, T currRef, T newRefValue) {}

  @Override
  public <T> void logRefUpdate(String project, String refName, T newRefValue) {}
}
