package com.googlesource.gerrit.plugins.multisite.index;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.forwarder.events.ChangeIndexEvent;
import java.util.Optional;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@TestPlugin(
    name = "multi-site",
    sysModule = "com.googlesource.gerrit.plugins.multisite.index.ChangeCheckerIT$TestModule")
public class ChangeCheckerIT extends LightweightPluginDaemonTest {
  private static final String TEST_BRANCH_REF_HEADS = RefNames.REFS_HEADS + "master";
  private static final String INEXISTENT_SHA1 = "d3d192b8f704aec0c764cca427fdaca88db71513";
  private ChangeCheckerImpl.Factory changeCheckerFactory;

  @Inject @GerritInstanceId String instanceId;

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      install(ChangeCheckerImpl.module());
    }
  }

  @Override
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();

    changeCheckerFactory = plugin.getSysInjector().getInstance(ChangeCheckerImpl.Factory.class);
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "test-instance")
  public void shouldBeUpToDateIfTargetBranchSHA1HasAdvanced() throws Exception {
    RevCommit headCommit = createCommit();
    int changeNum = newChangeNum();
    String changeId = changeProjectAndNum(changeNum);
    long changeCommitTs = changeCommitTs(changeNum);

    ChangeIndexEvent indexChangeEvent = newIndexChangeEvent(changeNum);
    indexChangeEvent.eventCreatedOn = changeCommitTs / 1000L;
    indexChangeEvent.targetSha = headCommit.getId().getName();

    RevCommit secondCommit = createCommit();
    assertThat(secondCommit.getId().getName()).isNotEqualTo(indexChangeEvent.targetSha);
    assertThat(changeCheckerFactory.create(changeId).isUpToDate(Optional.of(indexChangeEvent)))
        .isTrue();
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "test-instance")
  public void shouldNotUpToDateIfTargetSHA1Absent() throws Exception {
    int changeNum = newChangeNum();
    String changeId = changeProjectAndNum(changeNum);
    long changeCommitTs = changeCommitTs(changeNum);

    ChangeIndexEvent indexChangeEvent = newIndexChangeEvent(changeNum);
    indexChangeEvent.eventCreatedOn = changeCommitTs / 1000L;
    indexChangeEvent.targetSha = INEXISTENT_SHA1;

    assertThat(changeCheckerFactory.create(changeId).isUpToDate(Optional.of(indexChangeEvent)))
        .isFalse();
  }

  private ChangeIndexEvent newIndexChangeEvent(int changeNum) {
    ChangeIndexEvent indexChangeEvent =
        new ChangeIndexEvent(project.get(), changeNum, false, instanceId);
    return indexChangeEvent;
  }

  private int newChangeNum() throws Exception {
    PushOneCommit.Result changeRes = createChange();
    changeRes.assertOkStatus();
    int changeNum = changeRes.getChange().getId().get();
    return changeNum;
  }

  private String changeProjectAndNum(int changeNum) {
    String changeId = String.format("%s~%d", project.get(), changeNum);
    return changeId;
  }

  private long changeCommitTs(int changeNum) throws RestApiException {
    long changeCommitTs = gApi.changes().id(project.get(), changeNum).get().updated.getTime();
    return changeCommitTs;
  }

  private RevCommit createCommit() throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result result = push.to(TEST_BRANCH_REF_HEADS);
    result.assertOkStatus();
    return result.getCommit();
  }
}
