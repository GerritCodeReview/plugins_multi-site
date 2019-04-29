package com.googlesource.gerrit.plugins.multisite.validation;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertFalse;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE;

import com.google.gerrit.metrics.DisabledMetricMaker;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DryRunSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZkSharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZookeeperTestContainerSupport;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.curator.retry.RetryNTimes;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

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

  @Test
  public void immutableChangeShouldNotBeWrittenIntoZk() throws Exception {

    String AN_IMMUTABLE_REF = "refs/changes/01/1/1";

    List<ReceiveCommand> cmds = Arrays.asList(new ReceiveCommand(A, B, AN_IMMUTABLE_REF, UPDATE));

    BatchRefUpdate batchRefUpdate = newBatchUpdate(cmds);
    RefUpdateValidator RefUpdateValidator =
        newDefaultValidator(A_TEST_PROJECT_NAME, batchRefUpdate);

    RefUpdateValidator.executeBatchUpdate(batchRefUpdate, () -> execute(batchRefUpdate));

    assertFalse(zkSharedRefDatabase.exists(A_TEST_PROJECT_NAME, AN_IMMUTABLE_REF));
  }

  @Test
  public void compareAndPutShouldSucceedIfTheObjectionHasNotTheExpectedValueWithDesiredEnforcement()
      throws Exception {
    String projectName = "All-Users";
    String externalIds = "refs/meta/external-ids";

    List<ReceiveCommand> cmds = Arrays.asList(new ReceiveCommand(A, B, externalIds, UPDATE));

    BatchRefUpdate batchRefUpdate = newBatchUpdate(cmds);
    RefUpdateValidator RefUpdateValidator = newDefaultValidator(projectName, batchRefUpdate);

    Ref zkExistingRef = zkSharedRefDatabase.newRef(externalIds, B);
    zookeeperContainer.createRefInZk(projectName, zkExistingRef);

    RefUpdateValidator.executeBatchUpdate(batchRefUpdate, () -> execute(batchRefUpdate));

    assertThat(zookeeperContainer.readRefValueFromZk(projectName, zkExistingRef)).isEqualTo(B);
  }

  @Test
  public void compareAndPutShouldAlwaysIngoreAlwaysDraftCommentsEvenOutOfOrder() throws Exception {
    String DRAFT_COMMENT = "refs/draft-comments/56/450756/1013728";
    List<ReceiveCommand> cmds = Arrays.asList(new ReceiveCommand(A, B, DRAFT_COMMENT, UPDATE));

    BatchRefUpdate batchRefUpdate = newBatchUpdate(cmds);
    RefUpdateValidator RefUpdateValidator =
        newDefaultValidator(A_TEST_PROJECT_NAME, batchRefUpdate);

    RefUpdateValidator.executeBatchUpdate(batchRefUpdate, () -> execute(batchRefUpdate));

    assertFalse(zkSharedRefDatabase.exists(A_TEST_PROJECT_NAME, DRAFT_COMMENT));
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
