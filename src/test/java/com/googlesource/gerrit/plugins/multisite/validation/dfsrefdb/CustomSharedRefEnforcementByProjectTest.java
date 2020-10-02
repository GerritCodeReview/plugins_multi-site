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

import static com.google.common.truth.Truth.assertThat;

import com.googlesource.gerrit.plugins.multisite.Configuration;
import com.googlesource.gerrit.plugins.multisite.Configuration.SharedRefDatabase;
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
    Config sharedRefDbConfig = new Config();
    sharedRefDbConfig.setStringList(
        SharedRefDatabase.SECTION,
        SharedRefDatabase.SUBSECTION_ENFORCEMENT_RULES,
        EnforcePolicy.IGNORED.name(),
        Arrays.asList(
            "ProjectOne",
            "ProjectTwo:refs/heads/master/test",
            "ProjectTwo:refs/heads/master/test2"));

    refEnforcement = newCustomRefEnforcement(sharedRefDbConfig);
  }

  @Test
  public void projectOneShouldReturnDesiredForAllRefs() {
    Ref aRef = newRef("refs/heads/master/2", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("ProjectOne", aRef.getName()))
        .isEqualTo(EnforcePolicy.IGNORED);
  }

  @Test
  public void projectOneEnforcementShouldAlwaysPrevail() {
    Ref aRef = newRef("refs/heads/master/test", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("ProjectOne", aRef.getName()))
        .isEqualTo(EnforcePolicy.IGNORED);
  }

  @Test
  public void aNonListedProjectShouldRequireRefForMasterTest() {
    Ref aRef = newRef("refs/heads/master/test", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("NonListedProject", aRef.getName()))
        .isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void projectTwoSpecificRefShouldReturnIgnoredPolicy() {
    Ref refOne = newRef("refs/heads/master/test", AN_OBJECT_ID_1);
    Ref refTwo = newRef("refs/heads/master/test2", AN_OBJECT_ID_1);

    assertThat(refEnforcement.getPolicy("ProjectTwo", refOne.getName()))
        .isEqualTo(EnforcePolicy.IGNORED);
    assertThat(refEnforcement.getPolicy("ProjectTwo", refTwo.getName()))
        .isEqualTo(EnforcePolicy.IGNORED);
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
  public void getProjectPolicyForProjectOneShouldReturnIgnored() {
    assertThat(refEnforcement.getPolicy("ProjectOne")).isEqualTo(EnforcePolicy.IGNORED);
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
        newCustomRefEnforcementWithValue(EnforcePolicy.IGNORED, ":refs/heads/master");

    assertThat(customEnforcement.getPolicy("NonListedProject")).isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void getANonListedProjectWhenOnlyOneProjectIsListedShouldReturnRequired() {
    SharedRefEnforcement customEnforcement =
        newCustomRefEnforcementWithValue(EnforcePolicy.IGNORED, "AProject:");
    assertThat(customEnforcement.getPolicy("NonListedProject", "refs/heads/master"))
        .isEqualTo(EnforcePolicy.REQUIRED);
  }

  private SharedRefEnforcement newCustomRefEnforcementWithValue(
      EnforcePolicy policy, String... projectAndRefs) {
    Config sharedRefDbConfiguration = new Config();
    sharedRefDbConfiguration.setStringList(
        SharedRefDatabase.SECTION,
        SharedRefDatabase.SUBSECTION_ENFORCEMENT_RULES,
        policy.name(),
        Arrays.asList(projectAndRefs));
    return newCustomRefEnforcement(sharedRefDbConfiguration);
  }

  private SharedRefEnforcement newCustomRefEnforcement(Config sharedRefDbConfig) {
    return new CustomSharedRefEnforcementByProject(
        new Configuration(sharedRefDbConfig, new Config()));
  }

  @Override
  public String testBranch() {
    return "fooBranch";
  }
}
