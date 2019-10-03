package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import static com.google.common.truth.Truth.assertThat;

import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import org.eclipse.jgit.lib.Ref;
import org.junit.Test;

public class NoopSharedRefDatabaseTest implements RefFixture {

  private Ref sampleRef = newRef(A_TEST_REF_NAME, AN_OBJECT_ID_1);
  private NoopSharedRefDatabase objectUnderTest = new NoopSharedRefDatabase();

  @Test
  public void isUpToDateShouldAlwaysReturnTrue() {
    assertThat(objectUnderTest.isUpToDate(A_TEST_PROJECT_NAME_KEY, sampleRef)).isTrue();
  }

  @Test
  public void compareAndPutShouldAlwaysReturnTrue() {
    assertThat(objectUnderTest.compareAndPut(A_TEST_PROJECT_NAME_KEY, sampleRef, AN_OBJECT_ID_2))
        .isTrue();
  }

  @Test
  public void existsShouldAlwaysReturnFalse() {
    assertThat(objectUnderTest.exists(A_TEST_PROJECT_NAME_KEY, A_TEST_REF_NAME)).isFalse();
  }
}
