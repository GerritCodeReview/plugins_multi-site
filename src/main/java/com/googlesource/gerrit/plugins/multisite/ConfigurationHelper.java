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

package com.googlesource.gerrit.plugins.multisite;

import static com.google.common.base.Suppliers.memoize;
import static com.google.common.base.Suppliers.ofInstance;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigurationHelper {

  private static final Logger log = LoggerFactory.getLogger(ConfigurationHelper.class);

  public static int getInt(
      Supplier<Config> cfg, String section, String subSection, String name, int defaultValue) {
    return getInt(cfg.get(), section, subSection, name, defaultValue);
  }

  public static int getInt(
      Config cfg, String section, String subSection, String name, int defaultValue) {
    return cfg.getInt(section, subSection, name, defaultValue);
  }

  public static String getString(
      Supplier<Config> cfg, String section, String subsection, String name, String defaultValue) {
    return getString(cfg.get(), section, subsection, name, defaultValue);
  }

  public static String getString(
      Config cfg, String section, String subsection, String name, String defaultValue) {
    String value = cfg.getString(section, subsection, name);
    if (!Strings.isNullOrEmpty(value)) {
      return value;
    }
    return defaultValue;
  }

  public static long getLong(
      Supplier<Config> cfg, String section, String subSection, String name, long defaultValue) {
    return getLong(cfg.get(), section, subSection, name, defaultValue);
  }

  public static long getLong(
      Config cfg, String section, String subSection, String name, long defaultValue) {
    return cfg.getLong(section, subSection, name, defaultValue);
  }

  public static Supplier<Config> lazyLoad(Config config) {
    if (config instanceof FileBasedConfig) {
      return memoize(
          () -> {
            FileBasedConfig fileConfig = (FileBasedConfig) config;
            String fileConfigFileName = fileConfig.getFile().getPath();
            try {
              log.info("Loading configuration from {}", fileConfigFileName);
              fileConfig.load();
            } catch (IOException | ConfigInvalidException e) {
              log.error("Unable to load configuration from " + fileConfigFileName, e);
            }
            return fileConfig;
          });
    }
    return ofInstance(config);
  }

  public static List<String> getList(
      Supplier<Config> cfg, String section, String subsection, String name) {
    return ImmutableList.copyOf(cfg.get().getStringList(section, subsection, name));
  }

  public static boolean getBoolean(
      Supplier<Config> cfg, String section, String subsection, String name, boolean defaultValue) {
    return getBoolean(cfg.get(), section, subsection, name, defaultValue);
  }

  public static boolean getBoolean(
      Config cfg, String section, String subsection, String name, boolean defaultValue) {
    return cfg.getBoolean(section, subsection, name, defaultValue);
  }
}
