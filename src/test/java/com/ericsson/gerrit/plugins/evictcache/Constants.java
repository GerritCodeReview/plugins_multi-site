// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.evictcache;

final class Constants {

  private Constants() {
  }

  static final String URL = "http://localhost:18888";
  static final String ENDPOINT_BASE = "/plugins/evict-cache/gerrit/evict/";
  static final String PROJECT_LIST = "project_list";
  static final String ACCOUNTS = "accounts";
  static final String GROUPS = "groups";
  static final String GROUPS_BYINCLUDE = "groups_byinclude";
  static final String GROUPS_MEMBERS = "groups_members";
  static final String DEFAULT = "projects";

  static final int PORT = 18888;
}
