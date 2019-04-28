package com.googlesource.gerrit.plugins.multisite.validation;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.metrics.DisabledMetricMaker;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.DryRunSharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZkSharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.ZookeeperTestContainerSupport;
import org.apache.curator.retry.RetryNTimes;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RefValidationTest implements RefFixture {
  @Rule public TestName nameRule = new TestName();

  ZookeeperTestContainerSupport zookeeperContainer;
  ZkSharedRefDatabase zkSharedRefDatabase;

  @Mock BatchRefUpdate batchRefUpdate;
  @Mock RefDatabase refDb;

  @Before
  public void setup() {

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

  @After
  public void cleanup() {
    zookeeperContainer.cleanup();
  }

//  @Test
//  public void immutableChangeShouldReturnTrue() throws Exception {
//    Ref changeRef = zkSharedRefDatabase.newRef("refs/changes/01/1/1", AN_OBJECT_ID_1);
//
//    boolean shouldReturnTrue =
//        zkSharedRefDatabase.compareAndPut(
//            A_TEST_PROJECT_NAME, SharedRefDatabase.NULL_REF, changeRef);
//
//    assertThat(shouldReturnTrue).isTrue();
//  }
//
//  @Test
//  public void compareAndPutShouldSucceedIfTheObjectionHasNotTheExpectedValueWithDesiredEnforcement()
//      throws Exception {
//    String projectName = "All-Users";
//    String externalIds = "refs/meta/external-ids";
//
//    Ref oldRef = zkSharedRefDatabase.newRef(externalIds, AN_OBJECT_ID_1);
//    Ref expectedRef = zkSharedRefDatabase.newRef(externalIds, AN_OBJECT_ID_2);
//
//    zookeeperContainer.createRefInZk(projectName, oldRef);
//
//    assertThat(zkSharedRefDatabase.compareAndPut(projectName, expectedRef, refOf(AN_OBJECT_ID_3)))
//        .isTrue();
//  }
//
//  @Test(expected = Exception.class)
//  public void immutableChangeShouldNotBeStored() throws Exception {
//    Ref changeRef = zkSharedRefDatabase.newRef(A_REF_NAME_OF_A_PATCHSET, AN_OBJECT_ID_1);
//    zkSharedRefDatabase.compareAndPut(A_TEST_PROJECT_NAME, SharedRefDatabase.NULL_REF, changeRef);
//    zookeeperContainer.readRefValueFromZk(A_TEST_PROJECT_NAME, changeRef);
//  }
//
//  @Test
//  public void compareAndPutShouldAlwaysIngoreAlwaysDraftCommentsEvenOutOfOrder() throws Exception {
//    // Test to reproduce a production bug where ignored refs were persisted in ZK because
//    // newRef == NULL
//
//    getDryRunRefValidator().executeBatchUpdate(() -> batchRefUpdate.execute(null, null));
//
//    Ref existingRef =
//        zkSharedRefDatabase.newRef("refs/draft-comments/56/450756/1013728", AN_OBJECT_ID_1);
//    Ref oldRefToIgnore =
//        zkSharedRefDatabase.newRef("refs/draft-comments/56/450756/1013728", AN_OBJECT_ID_2);
//    Ref nullRef = SharedRefDatabase.NULL_REF;
//    String projectName = A_TEST_PROJECT_NAME;
//
//    // This ref should be ignored even if newRef is null
//    assertThat(zkSharedRefDatabase.compareAndPut(A_TEST_PROJECT_NAME, existingRef, nullRef))
//        .isTrue();
//
//    // This ignored ref should also be ignored
//    assertThat(zkSharedRefDatabase.compareAndPut(projectName, oldRefToIgnore, nullRef)).isTrue();
//  }

  private RefValidator getDryRunRefValidator() {
    DryRunSharedRefEnforcement dryRunSharedRefEnforcement = new DryRunSharedRefEnforcement();
    return new RefValidator(
        zkSharedRefDatabase,
        new ValidationMetrics(new DisabledMetricMaker()),
        dryRunSharedRefEnforcement,
        batchRefUpdate,
        A_TEST_PROJECT_NAME,
        refDb);
  }

  private Ref refOf(ObjectId objectId) {
    return zkSharedRefDatabase.newRef(aBranchRef(), objectId);
  }

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }
}
