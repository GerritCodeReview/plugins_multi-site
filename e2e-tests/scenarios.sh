#!/bin/bash

# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

SSH_OPTS='-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no'

function call_gerrit {
  SERVER=$1
  shift
  ssh -p 29418 ${SSH_OPTS} admin@${SERVER} gerrit "$@"
}

function test_multi_site_plugins_were_loaded {
  SERVER=$1
  PLUGINS_TO_CHECK=(events-kafka multi-site replication websession-broker zookeeper-refdb)
  echo "Testing if [${PLUGINS_TO_CHECK[*]}] were loaded @${SERVER}"

  readarray -t GERRIT_PLUGINS < <((call_gerrit ${SERVER} plugin ls || \
    { echo >&2 "Getting list of plugins failed"; exit 1; }) | cut -d' ' -f1)
  GERRIT_PLUGINS=("${GERRIT_PLUGINS[@]:2}")
  readarray -t LOADED_PLUGINS < <(comm -12 <(printf '%s\n' "${PLUGINS_TO_CHECK[@]}") <(printf '%s\n' "${GERRIT_PLUGINS[@]}"))
  readarray -t MISSING_PLUGINS < <(comm -23 <(printf '%s\n' "${PLUGINS_TO_CHECK[@]}") <(printf '%s\n' "${LOADED_PLUGINS[@]}"))
  if [[ ${#MISSING_PLUGINS[*]} -eq 0 ]]; then
    echo >&2 "All plugins were loaded successfully @${SERVER}"
  else
    echo >&2 "Plugins [${MISSING_PLUGINS[*]}] was/were not loaded successfully @${SERVER}"
    exit 1
  fi
}

test_multi_site_plugins_were_loaded gerrit1 || exit 1;
test_multi_site_plugins_were_loaded gerrit2 || exit 1;
echo "All tests were finished successfully."
