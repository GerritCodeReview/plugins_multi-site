// Copyright (C) 2017 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.multisite;

import static com.googlesource.gerrit.plugins.multisite.Configuration.Cache.CACHE_SECTION;
import static com.googlesource.gerrit.plugins.multisite.Configuration.DEFAULT_THREAD_POOL_SIZE;
import static com.googlesource.gerrit.plugins.multisite.Configuration.Index.INDEX_SECTION;
import static com.googlesource.gerrit.plugins.multisite.Configuration.THREAD_POOL_SIZE_KEY;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

public class Setup implements InitStep {

  private final ConsoleUI ui;
  private final String pluginName;
  private final InitFlags flags;
  private final SitePaths site;

  private FileBasedConfig config;

  @Inject
  public Setup(ConsoleUI ui, @PluginName String pluginName, InitFlags flags, SitePaths site) {
    this.ui = ui;
    this.pluginName = pluginName;
    this.flags = flags;
    this.site = site;
  }

  @Override
  public void run() throws Exception {
    ui.message("\n");
    ui.header("%s Plugin", pluginName);

    if (ui.yesno(true, "Configure %s", pluginName)) {
      ui.header("Configuring %s", pluginName);
      Path pluginConfigFile = site.etc_dir.resolve(pluginName + ".config");
      config = new FileBasedConfig(pluginConfigFile.toFile(), FS.DETECTED);
      config.load();
      configureCacheSection();
      configureIndexSection();
      flags.cfg.setBoolean("database", "h2", "autoServer", true);
    }
  }

  private void configureCacheSection() {
    ui.header("Cache section");
    promptAndSetString(
        "Cache thread pool size",
        CACHE_SECTION,
        THREAD_POOL_SIZE_KEY,
        str(DEFAULT_THREAD_POOL_SIZE));
  }

  private void configureIndexSection() {
    ui.header("Index section");
    promptAndSetString(
        "Index thread pool size",
        INDEX_SECTION,
        THREAD_POOL_SIZE_KEY,
        str(DEFAULT_THREAD_POOL_SIZE));
  }

  private String promptAndSetString(
      String title, String section, String name, String defaultValue) {
    return promptAndSetString(title, section, null, name, defaultValue);
  }

  private String promptAndSetString(
      String title, String section, String subsection, String name, String defaultValue) {
    String oldValue = Strings.emptyToNull(config.getString(section, subsection, name));
    String newValue = ui.readString(oldValue != null ? oldValue : defaultValue, title);
    if (!Objects.equals(oldValue, newValue)) {
      if (!Strings.isNullOrEmpty(newValue)) {
        config.setString(section, subsection, name, newValue);
      } else {
        config.unset(section, subsection, name);
      }
    }
    return newValue;
  }

  private static String str(int n) {
    return Integer.toString(n);
  }

  @Override
  public void postRun() {}
}
