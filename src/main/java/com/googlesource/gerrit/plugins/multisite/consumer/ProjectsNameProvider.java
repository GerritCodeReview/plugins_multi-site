package com.googlesource.gerrit.plugins.multisite.consumer;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.project.ProjectQueryBuilder;
import com.google.gerrit.server.query.project.ProjectQueryProcessor;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class ProjectsNameProvider implements Provider<ImmutableList<ProjectData>> {

  private final Provider<ProjectQueryProcessor> projectQueryProcessor;
  private final ProjectQueryBuilder  projectQueryBuilder;

  @Inject
  public ProjectsNameProvider(Provider<ProjectQueryProcessor> projectQueryProcessor,  ProjectQueryBuilder  projectQueryBuilder) {
    this.projectQueryProcessor = projectQueryProcessor;
    this.projectQueryBuilder = projectQueryBuilder;
  }

  @Override
  public ImmutableList<ProjectData> get() {
    try {
      return projectQueryProcessor.get().query(projectQueryBuilder.parse("state:active")).entities();
    } catch (QueryParseException e) {
      throw new RuntimeException(e);
    }
  }
}
