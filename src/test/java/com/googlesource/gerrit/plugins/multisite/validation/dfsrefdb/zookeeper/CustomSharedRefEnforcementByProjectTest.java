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

package com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.zookeeper;

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase.newRef;

import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.Configuration.ZookeeperConfig;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.CustomSharedRefEnforcementByProject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement.EnforcePolicy;
import java.util.Arrays;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Test;

public class CustomSharedRefEnforcementByProjectTest implements RefFixture {

  SharedRefEnforcement refEnforcement;

  @Before
  public void setUp() {
    Config multiSiteConfig = new Config();
    multiSiteConfig.setStringList(
        ZookeeperConfig.SECTION,
        EnforcePolicy.DESIRED.name(),
        ZookeeperConfig.KEY_ENFORCEMENT_RULE,
        Arrays.asList(
            "ProjectOne",
            "ProjectTwo:refs/heads/master/test",
            "ProjectTwo:refs/heads/master/test2"));
    multiSiteConfig.setString(
        ZookeeperConfig.SECTION,
        EnforcePolicy.IGNORED.name(),
        ZookeeperConfig.KEY_ENFORCEMENT_RULE,
        ":refs/heads/master/test");

    refEnforcement = newCustomRefEnforcement(multiSiteConfig);
  }

  @Test
  public void projectOneShouldReturnDesiredForAllRefs() {
    Ref aRef = newRef("refs/heads/master/2", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("ProjectOne", aRef.getName()))
        .isEqualTo(EnforcePolicy.DESIRED);
  }

  @Test
  public void projectOneEnforcementShouldAlwaysPrevail() {
    Ref aRef = newRef("refs/heads/master/test", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("ProjectOne", aRef.getName()))
        .isEqualTo(EnforcePolicy.DESIRED);
  }

  @Test
  public void aNonListedProjectShouldIgnoreRefForMasterTest() {
    Ref aRef = newRef("refs/heads/master/test", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("NonListedProject", aRef.getName()))
        .isEqualTo(EnforcePolicy.IGNORED);
  }

  @Test
  public void projectTwoSpecificRefShouldReturnDesiredPolicy() {
    Ref refOne = newRef("refs/heads/master/test", AN_OBJECT_ID_1);
    Ref refTwo = newRef("refs/heads/master/test2", AN_OBJECT_ID_1);

    assertThat(refEnforcement.getPolicy("ProjectTwo", refOne.getName()))
        .isEqualTo(EnforcePolicy.DESIRED);
    assertThat(refEnforcement.getPolicy("ProjectTwo", refTwo.getName()))
        .isEqualTo(EnforcePolicy.DESIRED);
  }

  @Test
  public void aNonListedProjectShouldReturnRequired() {
    Ref refOne = newRef("refs/heads/master/newChange", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("NonListedProject", refOne.getName()))
        .isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void aNonListedRefInProjectShouldReturnRequired() {
    Ref refOne = newRef("refs/heads/master/test3", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("ProjectTwo", refOne.getName()))
        .isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void aNonListedProjectAndRefShouldReturnRequired() {
    Ref refOne = newRef("refs/heads/master/test3", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("NonListedProject", refOne.getName()))
        .isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void getProjectPolicyForProjectOneShouldRetrunDesired() {
    assertThat(refEnforcement.getPolicy("ProjectOne")).isEqualTo(EnforcePolicy.DESIRED);
  }

  @Test
  public void getProjectPolicyForProjectTwoShouldReturnRequired() {
    assertThat(refEnforcement.getPolicy("ProjectTwo")).isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void getProjectPolicyForNonListedProjectShouldReturnRequired() {
    assertThat(refEnforcement.getPolicy("NonListedProject")).isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void getProjectPolicyForNonListedProjectWhenSingleProject() {
    SharedRefEnforcement customEnforcement =
        newCustomRefEnforcementWithValue(EnforcePolicy.DESIRED, ":refs/heads/master");

    assertThat(customEnforcement.getPolicy("NonListedProject")).isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void getANonListedProjectWhenOnlyOneProjectIsListedShouldReturnRequired() {
    SharedRefEnforcement customEnforcement =
        newCustomRefEnforcementWithValue(EnforcePolicy.DESIRED, "AProject:");
    assertThat(customEnforcement.getPolicy("NonListedProject", "refs/heads/master"))
        .isEqualTo(EnforcePolicy.REQUIRED);
  }

  private SharedRefEnforcement newCustomRefEnforcementWithValue(
      EnforcePolicy policy, String... projectAndRefs) {
    Config multiSiteConfig = new Config();
    multiSiteConfig.setStringList(
        ZookeeperConfig.SECTION,
        policy.name(),
        ZookeeperConfig.KEY_ENFORCEMENT_RULE,
        Arrays.asList(projectAndRefs));
    return newCustomRefEnforcement(multiSiteConfig);
  }

  private SharedRefEnforcement newCustomRefEnforcement(Config multiSiteConfig) {
    return new CustomSharedRefEnforcementByProject(
        new Configuration(multiSiteConfig, new Config()));
  }

  @Override
  public String testBranch() {
    return "fooBranch";
  }
}
