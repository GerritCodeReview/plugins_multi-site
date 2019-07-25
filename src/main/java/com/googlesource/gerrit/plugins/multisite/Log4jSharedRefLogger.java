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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.util.SystemLog;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.apache.log4j.PatternLayout;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Log4jSharedRefLogger extends LibModuleLogFile implements SharedRefLogger {
  private static final String LOG_NAME = "sharedref_log";
  private final Logger sharedRefDBLog;
  private final GitRepositoryManager gitRepositoryManager;
  private static final Gson gson = OutputFormat.JSON_COMPACT.newGson();

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  public Log4jSharedRefLogger(SystemLog systemLog, GitRepositoryManager gitRepositoryManager) {
    super(systemLog, LOG_NAME, new PatternLayout("[%d{ISO8601}] [%t] %-5p : %m%n"));
    this.gitRepositoryManager = gitRepositoryManager;
    sharedRefDBLog = LoggerFactory.getLogger(LOG_NAME);
  }

  @Override
  public void logRefUpdate(String project, Ref currRef, ObjectId newRefValue) {
    if (!ObjectId.zeroId().equals(newRefValue)) {
      try (Repository repository =
              gitRepositoryManager.openRepository(new Project.NameKey(project));
          RevWalk walk = new RevWalk(repository)) {
        RevCommit commit = walk.parseCommit(newRefValue);

        sharedRefDBLog.info(
            gson.toJson(
                new SharedRefLogEntry.UpdateRef(
                    project,
                    currRef.getName(),
                    currRef.getObjectId().getName(),
                    newRefValue.getName(),
                    CommonConverters.toGitPerson(commit.getCommitterIdent()),
                    commit.getShortMessage())));
      } catch (IOException e) {
        logger.atSevere().withCause(e).log(
            "Cannot log sharedRefDB interaction for ref %s on project %s",
            currRef.getName(), project);
      }

    } else {
      sharedRefDBLog.info(
          gson.toJson(
              new SharedRefLogEntry.DeleteRef(
                  project, currRef.getName(), currRef.getObjectId().getName())));
    }
  }

  @Override
  public void logProjectDelete(String project) {
    sharedRefDBLog.info(gson.toJson(new SharedRefLogEntry.DeleteProject(project)));
  }
}
