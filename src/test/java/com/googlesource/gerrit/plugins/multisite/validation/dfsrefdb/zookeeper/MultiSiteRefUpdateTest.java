package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import static junit.framework.TestCase.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.MultiSiteRefUpdate;
import java.io.IOException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class MultiSiteRefUpdateTest extends AbstractDaemonTest implements RefFixture {

  private InMemoryRepository db;
  private TestRepository<InMemoryRepository> inMemServerTestRepo;

  @Before
  public void setup() throws IOException {
    inMemServerTestRepo =
        new TestRepository<>((InMemoryRepository) repoManager.openRepository(project));
    db = inMemServerTestRepo.getRepository();
  }

  private MultiSiteRefUpdate updateRef(String name) throws IOException {
    final MultiSiteRefUpdate ref =
        (MultiSiteRefUpdate) db.updateRef(name); // casting not possible...
    ref.setNewObjectId(db.resolve(Constants.HEAD));
    return ref;
  }

  @Rule public TestName nameRule = new TestName();

  @Override
  public String testBranch() {
    return "branch_" + nameRule.getMethodName();
  }

  @Test
  public void commonRefUpdateShouldPass() throws IOException {
    final String newRef = "refs/heads/abc";
    final MultiSiteRefUpdate ru = updateRef(newRef);
    final ObjectId newObjectId = ru.getNewObjectId();
    ru.setNewObjectId(newObjectId);
    Result update = ru.update();

    assertEquals(Result.NEW, update);
  }
}
