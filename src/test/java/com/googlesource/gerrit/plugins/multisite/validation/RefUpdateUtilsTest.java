package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public class RefUpdateUtilsTest {

    @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();
    @Rule public TemporaryFolder gitFolder = new TemporaryFolder();
    @Rule public TestName nameRule = new TestName();

    @Inject private ProjectConfig.Factory projectConfigFactory;

    private TestRepository <InMemoryRepository> repo;

    @Inject private InMemoryRepositoryManager repoManager;

    private ProjectConfig project;
    String testRepoName = "testRepo";
    RevCommit masterCommit;


    @Before
    public void setUp() throws Exception {
        Project.NameKey name = new Project.NameKey(testRepoName);
        InMemoryRepository inMemoryRepo = repoManager.createRepository(name);
        project = projectConfigFactory.create(name);
        project.load(inMemoryRepo);
        repo = new TestRepository<>(inMemoryRepo);
        masterCommit = repo.branch("master").commit().create();
    }

    @Test
    public void shouldReturnVersioningReceiveCommandWhenCreatingVersion() {
        Optional<ReceiveCommand> optionalReceiveCommand = RefUpdateUtils.getVersioningCommand(repoManager, testRepoName);
        assertThat(optionalReceiveCommand).isNotEqualTo(Optional.empty());
        assertThat(optionalReceiveCommand.get().getOldId()).isEqualTo(ObjectId.zeroId());
    }

    @Test
    public void shouldReturnVersioningReceiveCommandWhenUpdatingVersion() {
        try {
            repo.branch(RefUpdateUtils.MULTI_SITE_VERSIONING_REF).commit().parent(masterCommit).create();
        } catch (Exception e) {
            fail("What the fork! " + e.getMessage());
        }
        Optional<ReceiveCommand> optionalReceiveCommand = RefUpdateUtils.getVersioningCommand(repoManager, testRepoName);
        assertThat(optionalReceiveCommand).isNotEqualTo(Optional.empty());
        assertThat(optionalReceiveCommand.get().getOldId()).isNotEqualTo(ObjectId.zeroId());
    }

    @Test
    public void shouldReturnEmptyWhenProjectDoesntExist() {
        Optional<ReceiveCommand> optionalReceiveCommand = RefUpdateUtils.getVersioningCommand(repoManager, "nonExistentRepo");
        assertThat(optionalReceiveCommand).isEqualTo(Optional.empty());
    }
}
