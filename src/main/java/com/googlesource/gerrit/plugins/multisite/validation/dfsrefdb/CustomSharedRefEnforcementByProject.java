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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomSharedRefEnforcementByProject implements SharedRefEnforcement {
  private static String ALL = ".*";
  private final Map<String, Map<String, EnforcePolicy>> PREDEF_ENFORCEMENTS;
  private final FluentLogger logger = FluentLogger.forEnclosingClass();

  public CustomSharedRefEnforcementByProject(List<String> enforcementRules) {
    logger.atInfo().log(
        String.format(
            "Running with Custom Shared Ref-Db Enforcement Policy with follosing rules %s",
            enforcementRules.toString()));
    this.PREDEF_ENFORCEMENTS = parseDryRunEnforcementsToMap(enforcementRules);
  }

  private Map<String, Map<String, EnforcePolicy>> parseDryRunEnforcementsToMap(
      List<String> dryRunRefEnforcement) {
    Map<String, Map<String, EnforcePolicy>> projectAndRefsEnforcements = new HashMap<>();
    try {
      for (String refEnforcement : dryRunRefEnforcement) {
        String[] projectAndRefWithPolicy = refEnforcement.split(":");
        assert (projectAndRefWithPolicy.length == 2);
        String projectName =
            projectAndRefWithPolicy[0].trim().isEmpty() ? ALL : projectAndRefWithPolicy[0].trim();
        String[] refAndPolicy = projectAndRefWithPolicy[1].split(",");
        assert (refAndPolicy.length == 2);
        String refName = refAndPolicy[0].trim().isEmpty() ? ALL : refAndPolicy[0].trim();

        Map<String, EnforcePolicy> existingOrDefaultRef =
            projectAndRefsEnforcements.getOrDefault(projectName, new HashMap<>());

        existingOrDefaultRef.put(
            refName, EnforcePolicy.valueOf(refAndPolicy[1].trim().toUpperCase()));

        projectAndRefsEnforcements.put(projectName, existingOrDefaultRef);
      }
    } catch (AssertionError e) {
      throw e;
    }
    return projectAndRefsEnforcements;
  }

  @Override
  public EnforcePolicy getPolicy(String projectName, String refName) {
    if (isRefToBeIgnoredBySharedRefDb(refName)) {
      return EnforcePolicy.IGNORED;
    }

    return getRefEnforcePolicy(projectName, refName);
  }

  private EnforcePolicy getRefEnforcePolicy(String projectName, String refName) {
    if (!PREDEF_ENFORCEMENTS.containsKey(projectName) && PREDEF_ENFORCEMENTS.containsKey(ALL)) {
      return PREDEF_ENFORCEMENTS.get(ALL).getOrDefault(refName, EnforcePolicy.REQUIRED);
    }

    EnforcePolicy policyFromProjectRefOrProjectAllRefs =
        PREDEF_ENFORCEMENTS.get(projectName).get(refName) == null
            ? PREDEF_ENFORCEMENTS.get(projectName).get(ALL)
            : PREDEF_ENFORCEMENTS.get(projectName).get(refName);

    return MoreObjects.firstNonNull(policyFromProjectRefOrProjectAllRefs, EnforcePolicy.REQUIRED);
  }

  @Override
  public EnforcePolicy getPolicy(String projectName) {
    Map<String, EnforcePolicy> policiesForProject =
        PREDEF_ENFORCEMENTS.getOrDefault(projectName, ImmutableMap.of());
    return policiesForProject.getOrDefault(ALL, EnforcePolicy.REQUIRED);
  }
}
