package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.googlesource.gerrit.plugins.multisite.SharedRefDatabaseWrapper;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.RefFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProjectDeletedSharedDbCleanupTest implements RefFixture {
  @Rule public TestName nameRule = new TestName();

  @Mock ValidationMetrics mockValidationMetrics;
  @Mock SharedRefDatabaseWrapper sharedRefDatabase;

  @Test
  public void aDeleteProjectEventShouldCleanupProjectFromZk() throws Exception {
    String projectName = A_TEST_PROJECT_NAME;
    ProjectDeletedSharedDbCleanup projectDeletedSharedDbCleanup =
        new ProjectDeletedSharedDbCleanup(sharedRefDatabase, mockValidationMetrics);

    ProjectDeletedListener.Event event =
        new ProjectDeletedListener.Event() {
          @Override
          public String getProjectName() {
            return projectName;
          }

          @Override
          public NotifyHandling getNotify() {
            return NotifyHandling.NONE;
          }
        };

    projectDeletedSharedDbCleanup.onProjectDeleted(event);

    Mockito.verify(sharedRefDatabase, Mockito.times(1)).remove(A_TEST_PROJECT_NAME_KEY);
  }
}
