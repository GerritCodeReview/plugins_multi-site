// Copyright (C) 2020 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

public class RefUpdateUtils {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String MULTI_SITE_VERSIONING_REF = "refs/multi-site/project-version";

  public static Optional<ReceiveCommand> getVersioningCommand(
      GitRepositoryManager gitRepositoryManager, String project) {
    ReceiveCommand receiveCommand;
    try {
      Repository repository = gitRepositoryManager.openRepository(Project.NameKey.parse(project));
      ObjectInserter ins = repository.newObjectInserter();
      ObjectId newId =
          ins.insert(OBJ_BLOB, Long.toString(Instant.now().toEpochMilli()).getBytes(UTF_8));
      Ref ref = repository.findRef(MULTI_SITE_VERSIONING_REF);
      ObjectId oldId = ref != null ? ref.getObjectId() : ObjectId.zeroId();
      receiveCommand = new ReceiveCommand(oldId, newId, MULTI_SITE_VERSIONING_REF);
      ins.flush();

      return Optional.of(receiveCommand);
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot create versioning command");
      return Optional.empty();
    }
  }
}
