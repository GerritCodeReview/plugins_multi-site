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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb;

import static com.google.common.base.Suppliers.memoize;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.multisite.ZookeeperConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CustomSharedRefEnforcementByProject implements SharedRefEnforcement {
  private static final String ALL = ".*";

  private final Supplier<Map<String, Map<String, EnforcePolicy>>> predefEnforcements;

  @Inject
  public CustomSharedRefEnforcementByProject(ZookeeperConfig config) {
    this.predefEnforcements = memoize(() -> parseDryRunEnforcementsToMap(config));
  }

  private static Map<String, Map<String, EnforcePolicy>> parseDryRunEnforcementsToMap(
      ZookeeperConfig config) {
    Map<String, Map<String, EnforcePolicy>> enforcementMap = new HashMap<>();

    for (Map.Entry<EnforcePolicy, String> enforcementEntry :
        config.getEnforcementRules().entries()) {
      parseEnforcementEntry(enforcementMap, enforcementEntry);
    }

    return enforcementMap;
  }

  private static void parseEnforcementEntry(
      Map<String, Map<String, EnforcePolicy>> enforcementMap,
      Map.Entry<EnforcePolicy, String> enforcementEntry) {
    Iterator<String> projectAndRef = Splitter.on(':').split(enforcementEntry.getValue()).iterator();
    EnforcePolicy enforcementPolicy = enforcementEntry.getKey();

    if (projectAndRef.hasNext()) {
      String projectName = emptyToAll(projectAndRef.next());
      String refName = emptyToAll(projectAndRef.hasNext() ? projectAndRef.next() : ALL);

      Map<String, EnforcePolicy> existingOrDefaultRef =
          enforcementMap.getOrDefault(projectName, new HashMap<>());

      existingOrDefaultRef.put(refName, enforcementPolicy);

      enforcementMap.put(projectName, existingOrDefaultRef);
    }
  }

  private static String emptyToAll(String value) {
    return value.trim().isEmpty() ? ALL : value;
  }

  @Override
  public EnforcePolicy getPolicy(String projectName, String refName) {
    if (isRefToBeIgnoredBySharedRefDb(refName)) {
      return EnforcePolicy.IGNORED;
    }

    return getRefEnforcePolicy(projectName, refName);
  }

  private EnforcePolicy getRefEnforcePolicy(String projectName, String refName) {
    Map<String, EnforcePolicy> orDefault =
        predefEnforcements
            .get()
            .getOrDefault(
                projectName, predefEnforcements.get().getOrDefault(ALL, ImmutableMap.of()));

    return MoreObjects.firstNonNull(
        orDefault.getOrDefault(refName, orDefault.get(ALL)), EnforcePolicy.REQUIRED);
  }

  @Override
  public EnforcePolicy getPolicy(String projectName) {
    Map<String, EnforcePolicy> policiesForProject =
        predefEnforcements
            .get()
            .getOrDefault(
                projectName, predefEnforcements.get().getOrDefault(ALL, ImmutableMap.of()));
    return policiesForProject.getOrDefault(ALL, EnforcePolicy.REQUIRED);
  }
}
