package com.googlesource.gerrit.plugins.multisite.validation;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SharedRefDbValidationTest implements RefFixture {

  @Mock SharedRefDatabase sharedRefDb;
  @Mock Repository localRepository;
  @Mock RefUpdate refUpdate;

  private Ref refOne =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, "refs/heads/master", AN_OBJECT_ID_1);
  private Ref refTwo =
      new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, "refs/for/changes/01/1/meta", AN_OBJECT_ID_2);

  public void initCommonMocks() throws IOException {
    doReturn(AN_OBJECT_ID_1).when(localRepository).resolve(refOne.getName());
    doReturn(AN_OBJECT_ID_2).when(localRepository).resolve(refTwo.getName());

    doNothing().when(refUpdate).setForceUpdate(true);
    doReturn(refUpdate).when(localRepository).updateRef("bar");
  }

  @Test
  public void remoteRefUpdatesShouldNotBeFilteredOutIfInSyncWithSharedRefDb() throws Exception {
    initCommonMocks();

    // Refs to push are the most up to date
    doReturn(true).when(sharedRefDb).isMostRecentVersion(A_TEST_PROJECT_NAME, refOne);
    doReturn(true).when(sharedRefDb).isMostRecentVersion(A_TEST_PROJECT_NAME, refTwo);

    RemoteRefUpdate remoteRefUpdateOne =
        new RemoteRefUpdate(
            localRepository, refOne.getName(), "foo", true, "bar", refOne.getObjectId());
    RemoteRefUpdate remoteRefUpdateTwo =
        new RemoteRefUpdate(
            localRepository, refTwo.getName(), "foo", true, "bar", refTwo.getObjectId());

    doReturn(refOne)
        .when(sharedRefDb)
        .newRef(remoteRefUpdateOne.getSrcRef(), remoteRefUpdateOne.getNewObjectId());
    doReturn(refTwo)
        .when(sharedRefDb)
        .newRef(remoteRefUpdateTwo.getSrcRef(), remoteRefUpdateTwo.getNewObjectId());

    Collection<RemoteRefUpdate> refsToPush = new ArrayList<>();
    refsToPush.add(remoteRefUpdateOne);
    refsToPush.add(remoteRefUpdateTwo);

    Collection<RemoteRefUpdate> filteredRefsToPush =
        SharedRefDbValidation.filterOutOfSyncRemoteRefUpdates(
            A_TEST_PROJECT_NAME, sharedRefDb, refsToPush);

    assertThat(filteredRefsToPush, is(refsToPush));
  }

  @Test
  public void remoteRefUpdatesShouldBeFilteredOutIfOutOfSyncWithSharedRefDb() throws Exception {
    initCommonMocks();

    // Refs to push are not the most up to date
    doReturn(false).when(sharedRefDb).isMostRecentVersion(A_TEST_PROJECT_NAME, refOne);
    doReturn(false).when(sharedRefDb).isMostRecentVersion(A_TEST_PROJECT_NAME, refTwo);

    RemoteRefUpdate remoteRefUpdateOne =
        new RemoteRefUpdate(
            localRepository, refOne.getName(), "foo", true, "bar", refOne.getObjectId());
    RemoteRefUpdate remoteRefUpdateTwo =
        new RemoteRefUpdate(
            localRepository, refTwo.getName(), "foo", true, "bar", refTwo.getObjectId());

    doReturn(refOne)
        .when(sharedRefDb)
        .newRef(remoteRefUpdateOne.getSrcRef(), remoteRefUpdateOne.getNewObjectId());
    doReturn(refTwo)
        .when(sharedRefDb)
        .newRef(remoteRefUpdateTwo.getSrcRef(), remoteRefUpdateTwo.getNewObjectId());

    Collection<RemoteRefUpdate> refsToPush = new ArrayList<>();
    refsToPush.add(remoteRefUpdateOne);
    refsToPush.add(remoteRefUpdateTwo);

    Collection<RemoteRefUpdate> filteredRefsToPush =
        SharedRefDbValidation.filterOutOfSyncRemoteRefUpdates(
            A_TEST_PROJECT_NAME, sharedRefDb, refsToPush);

    assertTrue(filteredRefsToPush.isEmpty());
  }

  @Rule public TestName nameRule = new TestName();

  @Override
  public String testBranch() {
    return null;
  }
}
