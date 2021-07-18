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

call_gerrit gerrit1 version || exit 1;
call_gerrit gerrit2 version || exit 1;
echo "All tests were finished successfully."
