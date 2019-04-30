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

import com.google.common.collect.ImmutableList;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.CustomSharedRefEnforcementByProject;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefDatabase;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement;
import com.googlesource.gerrit.plugins.multisite.validation.dfsrefdb.SharedRefEnforcement.EnforcePolicy;
import org.eclipse.jgit.lib.Ref;
import org.junit.Test;

public class CustomSharedRefEnforcementByProjectTest implements RefFixture {

  SharedRefEnforcement refEnforcement =
      new CustomSharedRefEnforcementByProject(
          ImmutableList.of(
              "ProjectOne:,desired", // all refs for the project
              ":refs/for/master/test,IGNORED", // this ref across all projects
              "ProjectTwo:refs/for/master/test,Desired",
              "ProjectTwo:refs/for/master/test2,Desired"));

  @Test
  public void projectOneShouldReturnDesiredForAllRefs() {
    Ref aRef = SharedRefDatabase.newRef("refs/for/master/2", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("ProjectOne", aRef.getName()))
        .isEqualTo(EnforcePolicy.DESIRED);
  }

  @Test
  public void projectOneEnforcementShouldAlwaysPrevail() {
    Ref aRef = SharedRefDatabase.newRef("refs/for/master/test", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("ProjectOne", aRef.getName()))
        .isEqualTo(EnforcePolicy.DESIRED);
  }

  @Test
  public void aNonListedProjectShouldIgnoreRefForMasterTest() {
    Ref aRef = SharedRefDatabase.newRef("refs/for/master/test", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("NonListedProject", aRef.getName()))
        .isEqualTo(EnforcePolicy.IGNORED);
  }

  @Test
  public void projectTwoSpecificRefShouldReturnDesiredPolicy() {
    Ref refOne = SharedRefDatabase.newRef("refs/for/master/test", AN_OBJECT_ID_1);
    Ref refTwo = SharedRefDatabase.newRef("refs/for/master/test2", AN_OBJECT_ID_1);

    assertThat(refEnforcement.getPolicy("ProjectTwo", refOne.getName()))
        .isEqualTo(EnforcePolicy.DESIRED);
    assertThat(refEnforcement.getPolicy("ProjectTwo", refTwo.getName()))
        .isEqualTo(EnforcePolicy.DESIRED);
  }

  @Test
  public void aNonListedProjectShouldReturnRequired() {
    Ref refOne = SharedRefDatabase.newRef("refs/for/master/newChange", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("NonListedProject", refOne.getName()))
        .isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void aNonListedRefInProjectShouldReturnRequired() {
    Ref refOne = SharedRefDatabase.newRef("refs/for/master/test3", AN_OBJECT_ID_1);
    assertThat(refEnforcement.getPolicy("ProjectTwo", refOne.getName()))
        .isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void aNonListedProjectAndRefShouldReturnRequired() {
    Ref refOne = SharedRefDatabase.newRef("refs/for/master/test3", AN_OBJECT_ID_1);
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
    CustomSharedRefEnforcementByProject customSharedRefEnforcementByProject =
        new CustomSharedRefEnforcementByProject(ImmutableList.of(":refs/for/master,desired"));
    assertThat(customSharedRefEnforcementByProject.getPolicy("NonListedProject"))
        .isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test
  public void getANonListedProjectWhenOnlyOneProjectIsListedShouldReturnRequired() {
    CustomSharedRefEnforcementByProject customSharedRefEnforcementByProject =
        new CustomSharedRefEnforcementByProject(ImmutableList.of("AProject:,desired"));
    assertThat(customSharedRefEnforcementByProject.getPolicy("NonListedProject", "refs/for/master"))
        .isEqualTo(EnforcePolicy.REQUIRED);
  }

  @Test(expected = AssertionError.class)
  public void wrongEnforcementProjectRuleSyntaxShouldFail() {
    new CustomSharedRefEnforcementByProject(ImmutableList.of("Project/refs/for/master,required"));
  }

  @Test(expected = AssertionError.class)
  public void wrongEnforcementRuleSyntaxMissingPolicyShouldFail() {
    new CustomSharedRefEnforcementByProject(ImmutableList.of("Project:refs/for/master"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void wrongPolicyEnforcementShouldFail() {
    new CustomSharedRefEnforcementByProject(ImmutableList.of("Project:refs/for/master,undefined"));
  }

  @Override
  public String testBranch() {
    return "fooBranch";
  }
}
