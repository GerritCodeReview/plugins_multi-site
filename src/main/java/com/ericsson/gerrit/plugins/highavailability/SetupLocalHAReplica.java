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

import static com.ericsson.gerrit.plugins.highavailability.Configuration.JGroups.CLUSTER_NAME_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.JGroups.DEFAULT_CLUSTER_NAME;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Main.DEFAULT_SHARED_DIRECTORY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Main.MAIN_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.Main.SHARED_DIRECTORY_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.PEER_INFO_SECTION;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.PeerInfo.STRATEGY_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.PeerInfoJGroups.JGROUPS_SUBSECTION;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.FileUtil;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

class SetupLocalHAReplica {
  private static final String DATABASE = "database";

  private final SitePaths masterSitePaths;
  private final FileBasedConfig masterConfig;
  private final Path sharedDir;

  private SitePaths replicaSitePaths;

  @Inject
  SetupLocalHAReplica(SitePaths masterSitePaths, InitFlags flags) {
    this.masterSitePaths = masterSitePaths;
    this.masterConfig = flags.cfg;
    this.sharedDir = masterSitePaths.site_path.resolve(DEFAULT_SHARED_DIRECTORY);
  }

  void run(SitePaths replica, FileBasedConfig pluginConfig)
      throws IOException, ConfigInvalidException {
    this.replicaSitePaths = replica;

    FileUtil.mkdirsOrDie(replicaSitePaths.site_path, "cannot create " + replicaSitePaths.site_path);

    configureMainSection(pluginConfig);
    configurePeerInfo(pluginConfig);

    for (Path dir : listDirsForCopy()) {
      copyFiles(dir);
    }

    mkdir(replicaSitePaths.logs_dir);
    mkdir(replicaSitePaths.tmp_dir);
    symlink(Paths.get(masterConfig.getString("gerrit", null, "basePath")));
    symlink(sharedDir);

    FileBasedConfig replicaConfig =
        new FileBasedConfig(replicaSitePaths.gerrit_config.toFile(), FS.DETECTED);
    replicaConfig.load();

    if ("h2".equals(masterConfig.getString(DATABASE, null, "type"))) {
      masterConfig.setBoolean(DATABASE, "h2", "autoServer", true);
      replicaConfig.setBoolean(DATABASE, "h2", "autoServer", true);
      symlinkH2ReviewDbDir();
    }
  }

  private List<Path> listDirsForCopy() throws IOException {
    ImmutableList.Builder<Path> toSkipBuilder = ImmutableList.builder();
    toSkipBuilder.add(
        masterSitePaths.resolve(masterConfig.getString("gerrit", null, "basePath")),
        masterSitePaths.db_dir,
        masterSitePaths.logs_dir,
        replicaSitePaths.site_path,
        masterSitePaths.site_path.resolve(sharedDir),
        masterSitePaths.tmp_dir);
    if ("h2".equals(masterConfig.getString(DATABASE, null, "type"))) {
      toSkipBuilder.add(
          masterSitePaths.resolve(masterConfig.getString(DATABASE, null, DATABASE)).getParent());
    }
    final ImmutableList<Path> toSkip = toSkipBuilder.build();

    final ArrayList<Path> dirsForCopy = new ArrayList<>();
    Files.walkFileTree(
        masterSitePaths.site_path,
        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
        Integer.MAX_VALUE,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            if (Files.isSameFile(dir, masterSitePaths.site_path)) {
              return FileVisitResult.CONTINUE;
            }

            Path p = masterSitePaths.site_path.relativize(dir);
            if (shouldSkip(p)) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            dirsForCopy.add(p);
            return FileVisitResult.CONTINUE;
          }

          private boolean shouldSkip(Path p) throws IOException {
            Path resolved = masterSitePaths.site_path.resolve(p);
            for (Path skip : toSkip) {
              if (skip.toFile().exists() && Files.isSameFile(resolved, skip)) {
                return true;
              }
            }
            return false;
          }
        });

    return dirsForCopy;
  }

  private void copyFiles(Path dir) throws IOException {
    final Path source = masterSitePaths.site_path.resolve(dir);
    final Path target = replicaSitePaths.site_path.resolve(dir);
    Files.createDirectories(target);
    Files.walkFileTree(
        source,
        EnumSet.noneOf(FileVisitOption.class),
        1,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Path f = source.relativize(file);
            if (file.toFile().isFile()) {
              Files.copy(file, target.resolve(f));
            }
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private static void mkdir(Path dir) throws IOException {
    Files.createDirectories(dir);
  }

  private void symlink(Path path) throws IOException {
    if (!path.isAbsolute()) {
      Files.createSymbolicLink(
          replicaSitePaths.site_path.resolve(path),
          masterSitePaths.site_path.resolve(path).toAbsolutePath().normalize());
    }
  }

  private void symlinkH2ReviewDbDir() throws IOException {
    symlink(Paths.get(masterConfig.getString(DATABASE, null, DATABASE)).getParent());
  }

  private void configureMainSection(FileBasedConfig pluginConfig) throws IOException {
    pluginConfig.setString(
        MAIN_SECTION,
        null,
        SHARED_DIRECTORY_KEY,
        masterSitePaths.site_path.relativize(sharedDir).toString());
    pluginConfig.save();
  }

  private static void configurePeerInfo(FileBasedConfig pluginConfig) throws IOException {
    pluginConfig.setString(PEER_INFO_SECTION, null, STRATEGY_KEY, "jgroups");
    pluginConfig.setString(
        PEER_INFO_SECTION, JGROUPS_SUBSECTION, CLUSTER_NAME_KEY, DEFAULT_CLUSTER_NAME);
    pluginConfig.save();
  }
}
