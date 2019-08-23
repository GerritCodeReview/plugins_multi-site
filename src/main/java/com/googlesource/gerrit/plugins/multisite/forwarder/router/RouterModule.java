package com.googlesource.gerrit.plugins.multisite.forwarder.router;

import com.google.inject.AbstractModule;

public class RouterModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(ForwardedIndexEventRouter.class).to(IndexEventRouter.class);
    bind(ForwardedCacheEvictionEventRouter.class).to(CacheEvictionEventRouter.class);
    bind(ForwardedProjectListUpdateRouter.class).to(ProjectListUpdateRouter.class);
    bind(ForwardedStreamEventRouter.class).to(StreamEventRouter.class);
  }
}
