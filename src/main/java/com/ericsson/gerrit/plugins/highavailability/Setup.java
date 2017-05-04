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

package com.ericsson.gerrit.plugins.highavailability;

import static com.ericsson.gerrit.plugins.highavailability.Configuration.*;

import com.ericsson.gerrit.plugins.highavailability.Configuration.PeerInfoStrategy;
import com.google.common.base.Strings;
import com.google.gerrit.common.FileUtil;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.pgm.init.api.ConsoleUI;
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
  private final SitePaths site;
  private FileBasedConfig config;

  @Inject
  public Setup(ConsoleUI ui, @PluginName String pluginName, SitePaths site) {
    this.ui = ui;
    this.pluginName = pluginName;
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
      configureMainSection();
      configurePeerInfoSection();
      configureHttp();
      configureCacheSection();
      configureIndexSection();
      configureWebsessiosSection();
      config.save();
    }
  }

  private void configureMainSection() {
    ui.header("Main section");
    String sharedDir =
        promptAndSetString("Shared directory", MAIN_SECTION, SHARED_DIRECTORY_KEY, null);
    if (!Strings.isNullOrEmpty(sharedDir)) {
      Path shared = site.site_path.resolve(sharedDir);
      FileUtil.mkdirsOrDie(shared, "cannot create " + shared);
    }
  }

  private void configurePeerInfoSection() {
    ui.header("PeerInfo section");
    PeerInfoStrategy strategy = ui.readEnum(PeerInfoStrategy.JGROUPS, "Peer info strategy");
    config.setEnum(PEER_INFO_SECTION, null, STRATEGY_KEY, strategy);
    if (strategy == PeerInfoStrategy.STATIC) {
      promptAndSetString("Peer URL", PEER_INFO_SECTION, STATIC_SUBSECTION, URL_KEY, null);
    } else {
      promptAndSetString(
          "JGroups cluster name",
          PEER_INFO_SECTION,
          JGROUPS_SUBSECTION,
          CLUSTER_NAME_KEY,
          DEFAULT_CLUSTER_NAME);
    }
  }

  private void configureHttp() {
    ui.header("Http section");
    promptAndSetString("User", HTTP_SECTION, USER_KEY, null);
    promptAndSetString("Password", HTTP_SECTION, PASSWORD_KEY, null);
    promptAndSetString(
        "Max number of tries to forward to remote peer",
        HTTP_SECTION,
        MAX_TRIES_KEY,
        str(DEFAULT_MAX_TRIES));
    promptAndSetString(
        "Retry interval [ms]", HTTP_SECTION, RETRY_INTERVAL_KEY, str(DEFAULT_RETRY_INTERVAL));
    promptAndSetString(
        "Connection timeout [ms]", HTTP_SECTION, CONNECTION_TIMEOUT_KEY, str(DEFAULT_TIMEOUT_MS));
    promptAndSetString(
        "Socket timeout [ms]", HTTP_SECTION, SOCKET_TIMEOUT_KEY, str(DEFAULT_TIMEOUT_MS));
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

  private void configureWebsessiosSection() {
    ui.header("Websession section");
    promptAndSetString(
        "Cleanup interval", WEBSESSION_SECTION, CLEANUP_INTERVAL_KEY, DEFAULT_CLEANUP_INTERVAL);
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
  public void postRun() throws Exception {}
}
