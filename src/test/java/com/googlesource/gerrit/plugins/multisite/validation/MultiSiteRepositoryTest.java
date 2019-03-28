package com.googlesource.gerrit.plugins.multisite.validation;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper.RefFixture;
import java.io.IOException;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultiSiteRepositoryTest implements RefFixture {

  @Mock MultiSiteRefDatabase.Factory multiSiteRefDbFactory;
  @Mock MultiSiteRefDatabase multiSiteRefDb;
  @Mock RefDatabase genericRefDb;

  @Mock MultiSiteRefUpdate multiSiteRefUpdate;

  @Mock Repository repository;
  @Mock BaseRepositoryBuilder repositoryBuilder;

  private final String PROJECT_NAME = "ProjectName";
  private final String REFS_HEADS_MASTER = "refs/heads/master";

  @Override
  public String testBranch() {
    return null;
  }

  private void setMockitoCommon() {
    doReturn(genericRefDb).when(repository).getRefDatabase();
    doReturn(multiSiteRefDb).when(multiSiteRefDbFactory).create(PROJECT_NAME, genericRefDb);
  }

  @Test
  public void shouldInvokeMultiSiteRefDbFactoryCreate() {
    setMockitoCommon();
    MultiSiteRepository multiSiteRepository =
        new MultiSiteRepository(multiSiteRefDbFactory, PROJECT_NAME, repository, repositoryBuilder);

    multiSiteRepository.getRefDatabase();
    verify(multiSiteRefDbFactory).create(PROJECT_NAME, genericRefDb);
  }

  @Test
  public void shouldInvokeNewUpdateInMultiSiteRefDatabase() throws IOException {
    setMockitoCommon();
    MultiSiteRepository multiSiteRepository =
        new MultiSiteRepository(multiSiteRefDbFactory, PROJECT_NAME, repository, repositoryBuilder);
    multiSiteRepository.getRefDatabase().newUpdate(REFS_HEADS_MASTER, false);

    verify(multiSiteRefDb).newUpdate(REFS_HEADS_MASTER, false);
  }

  @Test
  public void shouldInvokeUpdateInMultiSiteRefUpdate() throws IOException {
    setMockitoCommon();
    doReturn(Result.NEW).when(multiSiteRefUpdate).update();
    doReturn(multiSiteRefUpdate).when(multiSiteRefDb).newUpdate(REFS_HEADS_MASTER, false);

    MultiSiteRepository multiSiteRepository =
        new MultiSiteRepository(multiSiteRefDbFactory, PROJECT_NAME, repository, repositoryBuilder);

    Result updateResult =
        multiSiteRepository.getRefDatabase().newUpdate(REFS_HEADS_MASTER, false).update();

    verify(multiSiteRefUpdate).update();
    assertThat(updateResult).isEqualTo(Result.NEW);
  }
}
