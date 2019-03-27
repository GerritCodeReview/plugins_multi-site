package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import java.io.IOException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;

public class MultiSiteRepository extends InMemoryRepository {

  public MultiSiteRepository(DfsRepositoryDescription repoDesc) {
    super(repoDesc);
  }

  @Override
  public MultiSiteRefUpdate updateRef(String ref) throws IOException {
    return (MultiSiteRefUpdate) super.updateRef(ref);
  }

  @Override
  public MultiSiteRefUpdate updateRef(String ref, boolean detach) throws IOException {
    return (MultiSiteRefUpdate) super.updateRef(ref, detach);
  }
}
