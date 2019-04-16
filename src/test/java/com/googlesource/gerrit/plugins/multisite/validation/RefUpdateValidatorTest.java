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

import com.google.gerrit.metrics.DisabledMetricMaker;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DryRunSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZkSharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZookeeperTestContainerSupport;
import java.io.IOException;
import java.util.List;
import org.apache.curator.retry.RetryNTimes;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TestName;

@Ignore
public class RefUpdateValidatorTest extends LocalDiskRepositoryTestCase implements RefFixture {
  @Rule public TestName nameRule = new TestName();

  private Repository diskRepo;
  private TestRepository<Repository> repo;
  private RefDirectory refdir;
  private RevCommit A;
  private RevCommit B;

  ZookeeperTestContainerSupport zookeeperContainer;
  ZkSharedRefDatabase zkSharedRefDatabase;

  @Before
  public void setup() throws Exception {
    super.setUp();

    gitRepoSetup();
    zookeeperAndPolicyEnforcementSetup();
  }

  private void gitRepoSetup() throws Exception {
    diskRepo = createBareRepository();
    refdir = (RefDirectory) diskRepo.getRefDatabase();
    repo = new TestRepository<>(diskRepo);
    A = repo.commit().create();
    B = repo.commit(repo.getRevWalk().parseCommit(A));
  }

  private void zookeeperAndPolicyEnforcementSetup() {
    zookeeperContainer = new ZookeeperTestContainerSupport(false);
    int SLEEP_BETWEEN_RETRIES_MS = 30;
    long TRANSACTION_LOCK_TIMEOUT = 1000l;
    int NUMBER_OF_RETRIES = 5;

    zkSharedRefDatabase =
        new ZkSharedRefDatabase(
            zookeeperContainer.getCurator(),
            new ZkConnectionConfig(
                new RetryNTimes(NUMBER_OF_RETRIES, SLEEP_BETWEEN_RETRIES_MS),
                TRANSACTION_LOCK_TIMEOUT));
  }

  private RefUpdateValidator newDefaultValidator(
      String projectName, BatchRefUpdate batchRefUpdate) {
    return getRefValidatorForEnforcement(
        projectName, batchRefUpdate, new DefaultSharedRefEnforcement());
  }

  private RefUpdateValidator newDryRunRefValidator(
      String projectName, BatchRefUpdate batchRefUpdate) {
    return getRefValidatorForEnforcement(
        projectName, batchRefUpdate, new DryRunSharedRefEnforcement());
  }

  private RefUpdateValidator getRefValidatorForEnforcement(
      String projectName,
      BatchRefUpdate batchRefUpdate,
      SharedRefEnforcement dryRunSharedRefEnforcement) {
    return new RefUpdateValidator(
        zkSharedRefDatabase,
        new ValidationMetrics(new DisabledMetricMaker()),
        dryRunSharedRefEnforcement,
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
