package com.googlesource.gerrit.plugins.multisite.validation;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.LogThreshold;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.inject.AbstractModule;
import org.junit.Test;

@NoHttpd
@LogThreshold(level = "INFO")
@TestPlugin(
    name = "multi-site",
    sysModule = "com.googlesource.gerrit.plugins.multisite.validation.ValidationIT$Module")
public class ValidationIT extends LightweightPluginDaemonTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      install(new ValidationModule());
    }
  }

  @Test
  public void inSyncChangeValidatorShouldAcceptNewChange() throws Exception {
    final PushOneCommit.Result change = createChange("refs/for/master");

    change.assertOkStatus();
  }
}
