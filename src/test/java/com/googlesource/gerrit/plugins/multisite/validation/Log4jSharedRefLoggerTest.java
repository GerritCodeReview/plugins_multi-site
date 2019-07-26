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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.SystemLog;
import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.multisite.Log4jSharedRefLogger;
import com.googlesource.gerrit.plugins.multisite.SharedRefLogEntry;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.log4j.LogManager;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Log4jSharedRefLoggerTest extends AbstractDaemonTest {

  private static final Gson gson = OutputFormat.JSON_COMPACT.newGson();
  private StringWriter logWriter;
  private Log4jSharedRefLogger log4jSharedRefLogger;

  @Before
  public void setUp() throws IOException {
    this.logWriter = new StringWriter();
    this.log4jSharedRefLogger = newLog4jSharedRefLogger();
  }

  @Test
  public void shouldLogProjectDeletion() {
    log4jSharedRefLogger.logProjectDelete(project.get());

    SharedRefLogEntry.DeleteProject gotLogEntry =
        gson.fromJson(logWriter.toString(), SharedRefLogEntry.DeleteProject.class);

    assertThat(gotLogEntry.type).isEqualTo(SharedRefLogEntry.Type.DELETE_PROJECT);
    assertThat(gotLogEntry.projectName).isEqualTo(project.get());
  }

  @Test
  public void shouldLogUpdateRef() throws Exception {
    final String refName = "refs/remotes/origin/master";
    Ref currRef = repo().exactRef(refName);
    PushOneCommit.Result result = pushTo(refName);
    ObjectId newRefValue = result.getCommit().toObjectId();

    log4jSharedRefLogger.logRefUpdate(project.get(), currRef, newRefValue);

    SharedRefLogEntry.UpdateRef gotLogEntry =
        gson.fromJson(logWriter.toString(), SharedRefLogEntry.UpdateRef.class);

    assertThat(gotLogEntry.type).isEqualTo(SharedRefLogEntry.Type.UPDATE_REF);
    assertThat(gotLogEntry.projectName).isEqualTo(project.get());
    assertThat(gotLogEntry.refName).isEqualTo(refName);
    assertThat(gotLogEntry.oldId).isEqualTo(currRef.getObjectId().getName());
    assertThat(gotLogEntry.newId).isEqualTo(newRefValue.getName());
    assertThat(gotLogEntry.comment).isNotNull();
    assertThat(gotLogEntry.committer).isNotNull();
  }

  @Test
  public void shouldLogDeleteRef() throws Exception {
    final String refName = "refs/remotes/origin/master";
    Ref currRef = repo().exactRef(refName);

    log4jSharedRefLogger.logRefUpdate(project.get(), currRef, ObjectId.zeroId());

    SharedRefLogEntry.DeleteRef gotLogEntry =
        gson.fromJson(logWriter.toString(), SharedRefLogEntry.DeleteRef.class);

    assertThat(gotLogEntry.type).isEqualTo(SharedRefLogEntry.Type.DELETE_REF);
    assertThat(gotLogEntry.projectName).isEqualTo(project.get());
    assertThat(gotLogEntry.refName).isEqualTo(refName);
    assertThat(gotLogEntry.oldId).isEqualTo(currRef.getObjectId().getName());
  }

  @Test
  public void shouldLogBlobRefs() throws Exception {
    Repository allUsersRepo = repoManager.openRepository(allUsers);
    String blobRefName = RefNames.REFS_SEQUENCES + Sequences.NAME_ACCOUNTS;
    Ref currRef = allUsersRepo.exactRef(blobRefName);
    log4jSharedRefLogger.logRefUpdate(allUsers.get(), currRef, currRef.getObjectId());

    SharedRefLogEntry.UpdateRef gotLogEntry =
        gson.fromJson(logWriter.toString(), SharedRefLogEntry.UpdateRef.class);

    assertThat(gotLogEntry.type).isEqualTo(SharedRefLogEntry.Type.UPDATE_REF);
    assertThat(gotLogEntry.projectName).isEqualTo(allUsers.get());
    assertThat(gotLogEntry.refName).isEqualTo(blobRefName);
    assertThat(gotLogEntry.oldId).isEqualTo(currRef.getObjectId().getName());
    assertThat(gotLogEntry.newId).isEqualTo(currRef.getObjectId().getName());
    assertThat(gotLogEntry.comment).isNull();
    assertThat(gotLogEntry.committer).isNull();
  }

  private Log4jSharedRefLogger newLog4jSharedRefLogger() throws IOException {
    final Log4jSharedRefLogger log4jSharedRefLogger =
        new Log4jSharedRefLogger(new SystemLog(new SitePaths(newPath()), baseConfig), repoManager);
    log4jSharedRefLogger.setLogger(logWriterLogger());
    return log4jSharedRefLogger;
  }

  private Logger logWriterLogger() {
    org.apache.log4j.Logger logger = LogManager.getLogger("logWriterLogger");
    logger.addAppender(new WriterAppender(new PatternLayout("%m"), logWriter));
    return LoggerFactory.getLogger("logWriterLogger");
  }

  private static Path newPath() throws IOException {
    Path tmp = Files.createTempFile("gerrit_test_", "_site");
    Files.deleteIfExists(tmp);
    return tmp;
  }
}
