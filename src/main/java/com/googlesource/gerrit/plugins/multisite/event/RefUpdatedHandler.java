package com.googlesource.gerrit.plugins.multisite.event;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.multisite.validation.BatchRefUpdateValidator;
import com.googlesource.gerrit.plugins.multisite.validation.MultiSiteBatchRefUpdate;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.time.Instant;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

public class RefUpdatedHandler implements GitReferenceUpdatedListener {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private final GitRepositoryManager gitRepositoryManager;
    private final MultiSiteBatchRefUpdate.Factory multiSiteBatchRefUpdateFactory;

    @Inject
    RefUpdatedHandler(GitRepositoryManager gitRepositoryManager, MultiSiteBatchRefUpdate.Factory multiSiteBatchRefUpdateFactory) {
        this.gitRepositoryManager = gitRepositoryManager;
        this.multiSiteBatchRefUpdateFactory = multiSiteBatchRefUpdateFactory;
    }

    @Override
    public void onGitReferenceUpdated(Event event) {
        String projectName = event.getProjectName();
        logger.atInfo().log("Intercepted ref update " + event.getRefName() + " for project " + projectName);

        Repository repository;
        try {
             repository = gitRepositoryManager.openRepository(Project.NameKey.parse(projectName));
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Cannot open repository " + projectName);
            return;
        }

        MultiSiteBatchRefUpdate multiSiteBatchRefUpdate = multiSiteBatchRefUpdateFactory.create(projectName, repository.getRefDatabase());
        Optional<ReceiveCommand> optionalReceiveCommand = getVersioningCommand(repository);
        // XXX This is optional!!
        multiSiteBatchRefUpdate.addCommand(optionalReceiveCommand.get());

        try {
            RevWalk rev = new RevWalk(repository);
            multiSiteBatchRefUpdate.execute(rev, NullProgressMonitor.INSTANCE);
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Cannot create versioning command for " + event.getProjectName());
        }
    }

    public static final String MULTI_SITE_VERSIONING_REF = "refs/multi-site/project-version";

    public static Optional <ReceiveCommand> getVersioningCommand(Repository repository) {
        ReceiveCommand receiveCommand;

        try {
            ObjectInserter ins = repository.newObjectInserter();
            ObjectId newId =
                    ins.insert(OBJ_BLOB, Long.toString(Instant.now().toEpochMilli()).getBytes(UTF_8));
            Ref ref = repository.findRef(MULTI_SITE_VERSIONING_REF);
            ObjectId oldId = ref != null ? ref.getObjectId() : ObjectId.zeroId();
            receiveCommand = new ReceiveCommand(oldId, newId, MULTI_SITE_VERSIONING_REF);
            ins.flush();

            return Optional.of(receiveCommand);
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Cannot create versioning command");
            return Optional.empty();
        }
    }
}
