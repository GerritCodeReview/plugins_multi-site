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

package com.googlesource.gerrit.plugins.multisite;

import com.google.gerrit.extensions.common.GitPerson;

public class SharedRefLogEntry {

  public enum Type {
    UPDATE_BLOB,
    UPDATE_REF,
    DELETE_REF,
    DELETE_PROJECT
  }

  public String projectName;
  public Type type;

  public static class UpdateRef extends SharedRefLogEntry {

    public String refName;
    public String oldId;
    public String newId;
    public GitPerson committer;
    public String comment;

    UpdateRef(
        String projectName,
        String refName,
        String oldId,
        String newId,
        GitPerson committer,
        String comment) {
      this.type = Type.UPDATE_REF;
      this.projectName = projectName;
      this.refName = refName;
      this.oldId = oldId;
      this.newId = newId;
      this.committer = committer;
      this.comment = comment;
    }
  }

  public static class UpdateBlob extends SharedRefLogEntry {

    public String refName;
    public String oldId;
    public String newId;

    UpdateBlob(String projectName, String refName, String oldId, String newId) {
      this.type = Type.UPDATE_BLOB;
      this.projectName = projectName;
      this.refName = refName;
      this.oldId = oldId;
      this.newId = newId;
    }
  }

  public static class DeleteProject extends SharedRefLogEntry {

    DeleteProject(String projectName) {
      this.type = Type.DELETE_PROJECT;
      this.projectName = projectName;
    }
  }

  public static class DeleteRef extends SharedRefLogEntry {

    public String refName;
    public String oldId;

    DeleteRef(String projectName, String refName, String oldId) {
      this.type = Type.DELETE_REF;
      this.projectName = projectName;
      this.refName = refName;
      this.oldId = oldId;
    }
  }
}
