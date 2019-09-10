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

import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE;

import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.reviewdb.client.Project;
import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import java.io.IOException;
import java.util.Arrays;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
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

  @Before
  public void setup() throws Exception {
    super.setUp();

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
        batchRefUpdate, () -> execute(batchRefUpdate));

    Mockito.when(
            sharedRefDatabase.isUpToDate(
                A_TEST_PROJECT_NAME_KEY, newRef(AN_IMMUTABLE_REF, A.getId())))
        .thenReturn(true);

    Mockito.verify(sharedRefDatabase, Mockito.never())
        .compareAndPut(
            Mockito.any(Project.NameKey.class),
            Mockito.any(Ref.class),
            Mockito.any(ObjectId.class));
  }

  @Test
  public void compareAndPutShouldSucceedIfTheObjectionHasNotTheExpectedValueWithDesiredEnforcement()
      throws Exception {
    String projectName = "All-Users";
    Project.NameKey projectNameKey = new Project.NameKey("All-Users");
    String externalIds = "refs/meta/external-ids";

    List<ReceiveCommand> cmds = Arrays.asList(new ReceiveCommand(A, B, externalIds, UPDATE));

    BatchRefUpdate batchRefUpdate = newBatchUpdate(cmds);
    BatchRefUpdateValidator batchRefUpdateValidator = newDefaultValidator(projectName);

    Ref zkExistingRef = newRef(externalIds, B.getId());
    // In comment below we have original code, I'm wondering how this test works, here we are
    // explicitly
    // setting ref based on 'B' and then in line 128 we are checking if 'B' is in zk. Operation
    // which we want
    // to test is update from 'A' to 'B' so if update is successful 'B' is in the zk if not also 'B'
    // is in zk
    // because it was manually added

    //  zookeeperContainer.createRefInZk(projectNameKey, zkExistingRef);
    Mockito.when(sharedRefDatabase.isUpToDate(projectNameKey, zkExistingRef)).thenReturn(true);

    batchRefUpdateValidator.executeBatchUpdateWithValidation(
        batchRefUpdate, () -> execute(batchRefUpdate));

    //     zookeeperContainer.readRefValueFromZk(projectNameKey, zkExistingRef)).isEqualTo(B);
    Mockito.verify(sharedRefDatabase, Mockito.times(1))
        .compareAndPut(projectNameKey, zkExistingRef, B.getId());
  }

  @Test
  public void compareAndPutShouldAlwaysIngoreAlwaysDraftCommentsEvenOutOfOrder() throws Exception {
    String DRAFT_COMMENT = "refs/draft-comments/56/450756/1013728";
    List<ReceiveCommand> cmds = Arrays.asList(new ReceiveCommand(A, B, DRAFT_COMMENT, UPDATE));

    BatchRefUpdate batchRefUpdate = newBatchUpdate(cmds);
    BatchRefUpdateValidator BatchRefUpdateValidator = newDefaultValidator(A_TEST_PROJECT_NAME);

    BatchRefUpdateValidator.executeBatchUpdateWithValidation(
        batchRefUpdate, () -> execute(batchRefUpdate));

    Mockito.verify(sharedRefDatabase, Mockito.never())
        .compareAndPut(A_TEST_PROJECT_NAME_KEY, newRef(DRAFT_COMMENT, A.getId()), B.getId());
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
    return u;
  }

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }
}
