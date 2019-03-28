// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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

  private final MultiSiteRefDatabase.Factory multiSiteRefDbFactory;
  private final String projectName;
  private final Repository repository;

  public interface Factory {
    public MultiSiteRepository create(String projectName, Repository repository, BaseRepositoryBuilder repositoryBuilder);
  }

  @Inject
  public MultiSiteRepository(
      MultiSiteRefDatabase.Factory multiSiteRefDbFactory,
      @Assisted String projectName,
      @Assisted Repository repository,
      @Assisted BaseRepositoryBuilder repositoryBuilder) {
    super(repositoryBuilder);
    this.multiSiteRefDbFactory = multiSiteRefDbFactory;
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
    return multiSiteRefDbFactory.create(projectName, refDatabase);
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
