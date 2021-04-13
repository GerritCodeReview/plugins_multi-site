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
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.googlesource.gerrit.plugins.multisite.ProjectsFilter;
import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.validation.RefUpdateValidator.OneParameterVoidFunction;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BatchRefUpdateValidatorTest extends LocalDiskRepositoryTestCase implements RefFixture {
  @Rule public TestName nameRule = new TestName();

  private Repository diskRepo;
  private TestRepository<Repository> repo;
  private RefDirectory refdir;
  private RevCommit A;
  private RevCommit B;

  @Mock SharedRefDatabaseWrapper sharedRefDatabase;

  @Mock SharedRefEnforcement tmpRefEnforcement;

  @Mock ProjectsFilter projectsFilter;

  @Mock OneParameterVoidFunction<List<ReceiveCommand>> rollbackFunction;

  @Before
  public void setup() throws Exception {
    super.setUp();
    when(projectsFilter.matches(anyString())).thenReturn(true);
    gitRepoSetup();
  }

  private void gitRepoSetup() throws Exception {
    diskRepo = createBareRepository();
    refdir = (RefDirectory) diskRepo.getRefDatabase();
    repo = new TestRepository<>(diskRepo);
    A = repo.commit().create();
    B = repo.commit(repo.getRevWalk().parseCommit(A));
  }

  @Test
  public void immutableChangeShouldNotBeWrittenIntoZk() throws Exception {
    String AN_IMMUTABLE_REF = "refs/changes/01/1/1";

    List<ReceiveCommand> cmds = Arrays.asList(new ReceiveCommand(A, B, AN_IMMUTABLE_REF, UPDATE));

    BatchRefUpdate batchRefUpdate = newBatchUpdate(cmds);
    BatchRefUpdateValidator BatchRefUpdateValidator = newDefaultValidator(A_TEST_PROJECT_NAME);

    BatchRefUpdateValidator.executeBatchUpdateWithValidation(
        batchRefUpdate, () -> execute(batchRefUpdate), this::defaultRollback);

    verify(sharedRefDatabase, never())
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));
  }

  @Test
  public void compareAndPutShouldAlwaysIngoreAlwaysDraftCommentsEvenOutOfOrder() throws Exception {
    String DRAFT_COMMENT = "refs/draft-comments/56/450756/1013728";
    List<ReceiveCommand> cmds = Arrays.asList(new ReceiveCommand(A, B, DRAFT_COMMENT, UPDATE));

    BatchRefUpdate batchRefUpdate = newBatchUpdate(cmds);
    BatchRefUpdateValidator BatchRefUpdateValidator = newDefaultValidator(A_TEST_PROJECT_NAME);

    BatchRefUpdateValidator.executeBatchUpdateWithValidation(
        batchRefUpdate, () -> execute(batchRefUpdate), this::defaultRollback);

    verify(sharedRefDatabase, never())
        .compareAndPut(A_TEST_PROJECT_NAME_KEY, newRef(DRAFT_COMMENT, A.getId()), B.getId());
  }

  @Test
  public void validationShouldFailWhenLocalRefDbIsOutOfSync() throws Exception {
    String AN_OUT_OF_SYNC_REF = "refs/changes/01/1/1";
    BatchRefUpdate batchRefUpdate =
        newBatchUpdate(
            Collections.singletonList(new ReceiveCommand(A, B, AN_OUT_OF_SYNC_REF, UPDATE)));
    BatchRefUpdateValidator batchRefUpdateValidator =
        getRefValidatorForEnforcement(A_TEST_PROJECT_NAME, tmpRefEnforcement);

    doReturn(SharedRefEnforcement.EnforcePolicy.REQUIRED)
        .when(batchRefUpdateValidator.refEnforcement)
        .getPolicy(A_TEST_PROJECT_NAME, AN_OUT_OF_SYNC_REF);
    lenient()
        .doReturn(false)
        .when(sharedRefDatabase)
        .isUpToDate(A_TEST_PROJECT_NAME_KEY, newRef(AN_OUT_OF_SYNC_REF, AN_OBJECT_ID_1));

    batchRefUpdateValidator.executeBatchUpdateWithValidation(
        batchRefUpdate, () -> execute(batchRefUpdate), rollbackFunction);

    verify(rollbackFunction, never()).invoke(any());

    final List<ReceiveCommand> commands = batchRefUpdate.getCommands();
    assertThat(commands.size()).isEqualTo(1);
    commands.forEach(
        (command) -> assertThat(command.getResult()).isEqualTo(ReceiveCommand.Result.LOCK_FAILURE));
  }

  @Test
  public void shouldNotUpdateSharedRefDbWhenProjectIsLocal() throws Exception {
    when(projectsFilter.matches(anyString())).thenReturn(true);

    String AN_OUT_OF_SYNC_REF = "refs/changes/01/1/1";
    BatchRefUpdate batchRefUpdate =
        newBatchUpdate(
            Collections.singletonList(new ReceiveCommand(A, B, AN_OUT_OF_SYNC_REF, UPDATE)));
    BatchRefUpdateValidator batchRefUpdateValidator =
        getRefValidatorForEnforcement(A_TEST_PROJECT_NAME, tmpRefEnforcement);

    batchRefUpdateValidator.executeBatchUpdateWithValidation(
        batchRefUpdate, () -> execute(batchRefUpdate), this::defaultRollback);

    verify(sharedRefDatabase, never())
        .compareAndPut(any(Project.NameKey.class), any(Ref.class), any(ObjectId.class));
  }

  private BatchRefUpdateValidator newDefaultValidator(String projectName) {
    return getRefValidatorForEnforcement(projectName, new DefaultSharedRefEnforcement());
  }

  private BatchRefUpdateValidator getRefValidatorForEnforcement(
      String projectName, SharedRefEnforcement sharedRefEnforcement) {
    return new BatchRefUpdateValidator(
        sharedRefDatabase,
        new ValidationMetrics(new DisabledMetricMaker()),
        sharedRefEnforcement,
        new DummyLockWrapper(),
        projectsFilter,
        projectName,
        diskRepo.getRefDatabase());
  }

  private Void execute(BatchRefUpdate u) throws IOException {
    try (RevWalk rw = new RevWalk(diskRepo)) {
      u.execute(rw, NullProgressMonitor.INSTANCE);
    }
    return null;
  }

  private BatchRefUpdate newBatchUpdate(List<ReceiveCommand> cmds) {
    BatchRefUpdate u = refdir.newBatchUpdate();
    u.addCommand(cmds);
    cmds.forEach(c -> c.setResult(Result.OK));
    return u;
  }

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }

  private void defaultRollback(List<ReceiveCommand> cmds) throws IOException {
    // do nothing
  }
}
