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

import com.google.gerrit.server.notedb.NotesMigration;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GerritNoteDbStatus implements NoteDbStatus {
  private final NotesMigration notesMigration;

  @Inject
  public GerritNoteDbStatus(NotesMigration notesMigration) {
    this.notesMigration = notesMigration;
  }

  @Override
  public boolean enabled() {
    return notesMigration.commitChangeWrites();
  }
}
