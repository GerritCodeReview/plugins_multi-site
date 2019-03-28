package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

public class MultiSiteRepository extends Repository {

  private final MultiSiteRefDatabase.Factory multiSiteRefDatabase;
  private final String projectName;
  private final Repository repository;

  public interface Factory {
    public MultiSiteRepository create(String projectName, Repository repository);
  }

  @Inject
  public MultiSiteRepository(
      MultiSiteRefDatabase.Factory multiSiteRefDatabase,
      @Assisted String projectName,
      @Assisted Repository repository,
      @Assisted BaseRepositoryBuilder repositoryBuilder) {
    super(repositoryBuilder);
    this.multiSiteRefDatabase = multiSiteRefDatabase;
    this.projectName = projectName;
    this.repository = repository;
  }

  @Override
  public void create(boolean b) throws IOException {}

  @Override
  public ObjectDatabase getObjectDatabase() {
    return repository.getObjectDatabase();
  }

  @Override
  public RefDatabase getRefDatabase() {
    RefDatabase refDatabase = repository.getRefDatabase();
    return multiSiteRefDatabase.create(projectName, refDatabase);
  }

  @Override
  public StoredConfig getConfig() {
    return repository.getConfig();
  }

  @Override
  public AttributesNodeProvider createAttributesNodeProvider() {
    return repository.createAttributesNodeProvider();
  }

  @Override
  public void scanForRepoChanges() throws IOException {
    repository.scanForRepoChanges();
  }

  @Override
  public void notifyIndexChanged(boolean b) {
    repository.notifyIndexChanged(b);
  }

  @Override
  public ReflogReader getReflogReader(String s) throws IOException {
    return repository.getReflogReader(s);
  }
}
