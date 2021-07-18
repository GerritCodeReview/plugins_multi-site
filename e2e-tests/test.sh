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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
GERRIT_BRANCH=stable-3.4
GERRIT_CI=https://gerrit-ci.gerritforge.com/view/Plugins-$GERRIT_BRANCH/job
LAST_BUILD=lastSuccessfulBuild/artifact/bazel-bin/plugins
EVENTS_BROKER_VER=`grep 'com.gerritforge:events-broker' $(dirname $0)/../external_plugin_deps.bzl | cut -d '"' -f 2 | cut -d ':' -f 3`
GLOBAL_REFDB_VER=`grep 'com.gerritforge:global-refdb' $(dirname $0)/../external_plugin_deps.bzl | cut -d '"' -f 2 | cut -d ':' -f 3`
DEF_MULTISITE_LOCATION=${SCRIPT_DIR}/../../../bazel-bin/plugins/multi-site/multi-site.jar
DEF_REPLICATION_LOCATION=${SCRIPT_DIR}/../../../bazel-bin/plugins/replication/replication.jar

function check_application_requirements {
  type java >/dev/null 2>&1 || { echo >&2 "Require java but it's not installed. Aborting."; exit 1; }
  type docker >/dev/null 2>&1 || { echo >&2 "Require docker but it's not installed. Aborting."; exit 1; }
  type docker-compose >/dev/null 2>&1 || { echo >&2 "Require docker-compose but it's not installed. Aborting."; exit 1; }
  type wget >/dev/null 2>&1 || { echo >&2 "Require wget but it's not installed. Aborting."; exit 1; }
  type envsubst >/dev/null 2>&1 || { echo >&2 "Require envsubst but it's not installed. Aborting."; exit 1; }
  type openssl >/dev/null 2>&1 || { echo >&2 "Require openssl but it's not installed. Aborting."; exit 1; }
}

function setup_zookeeper_config {
  SOURCE_ZOOKEEPER_CONFIG=${SCRIPT_DIR}/../setup_local_env/configs/zookeeper-refdb.config
  DESTINATION_ZOOKEEPER_CONFIG=$1

  export ZK_HOST=zookeeper
  export ZK_PORT=2181
  
  echo "Replacing variables for file ${SOURCE_ZOOKEEPER_CONFIG} and copying to ${DESTINATION_ZOOKEEPER_CONFIG}"
  cat $SOURCE_ZOOKEEPER_CONFIG | envsubst | sed 's/#{name}#/${name}/g' > $DESTINATION_ZOOKEEPER_CONFIG
}

function setup_replication_config {
  SOURCE_REPLICATION_CONFIG=${SCRIPT_DIR}/../setup_local_env/configs/replication.config
  DESTINATION_REPLICATION_CONFIG=$1

  export REPLICATION_URL="url = $2"
  export REPLICATION_DELAY_SEC=1
  
  echo "Replacing variables for file ${SOURCE_REPLICATION_CONFIG} and copying to ${DESTINATION_REPLICATION_CONFIG}"
  cat $SOURCE_REPLICATION_CONFIG | envsubst | sed 's/#{name}#/${name}/g' > $DESTINATION_REPLICATION_CONFIG
}

function setup_gerrit_config {
  SOURCE_RGERRIT_CONFIG=${SCRIPT_DIR}/../setup_local_env/configs/gerrit.config
  DESTINATION_GERRIT_CONFIG=$1

  export BROKER_HOST=$2
  export BROKER_PORT=$3
  export INSTANCE_ID=$4
  export SSH_ADVERTISED_PORT=$5
  export GERRIT_CANONICAL_WEB_URL=http://localhost:8080
  export LOCATION_TEST_SITE=/var/gerrit
  export REMOTE_DEBUG_PORT=5005
  export GERRIT_SSHD_PORT=29418
  export HTTP_PROTOCOL=http
  export GERRIT_HTTPD_PORT=8080
  export FAKE_NFS=/var/gerrit/fake-nfs

  echo "Replacing variables for file ${SOURCE_RGERRIT_CONFIG} and copying to ${DESTINATION_GERRIT_CONFIG}"
  cat $SOURCE_RGERRIT_CONFIG | envsubst | sed 's/#{name}#/${name}/g' > $DESTINATION_GERRIT_CONFIG
}

function cleanup {
  echo "shutting down the setup"
  docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose-setup.yaml stop
  docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose-setup.yaml down -v

  echo "Do you wish to delete deployment dir [${DEPLOYMENT_LOCATION}]?"
  select yn in "Yes" "No"; do
    case $yn in
      Yes ) rm -rf ${DEPLOYMENT_LOCATION}; break;;
      No ) echo "Setup preserved in [${DEPLOYMENT_LOCATION}]";;
    esac
  done
}

# Check application requirements
check_application_requirements

while [ $# -ne 0 ]
do
case "$1" in
  "--help" )
    echo "Usage: sh $0 [--option $value]"
    echo
    echo "[--deployment-location]         Base location for the test deployment; default [/tmp/random_dir]"
    echo "[--multisite-lib-file]          Location to lib multi-site.jar file; defaults to [${DEF_MULTISITE_LOCATION}]"
    echo "[--replication-lib-file]        Location to lib replication.jar file; defaults to [${DEF_REPLICATION_LOCATION}]"
    echo "[--broker-type]                 events broker type; 'kafka', 'kinesis' or 'gcloud-pubsub'. Default 'kafka' TODO: so far only 'kafka'"
    echo
    exit 0
  ;;
  "--deployment-location" )
    DEPLOYMENT_LOCATION=$2
    shift
    shift
  ;;
  "--multisite-lib-file" )
    MULTISITE_LIB_LOCATION=$2
    shift
    shift
  ;;
  "--eplication-lib-file" )
    REPLICATION_LIB_LOCATION=$2
    shift
    shift
  ;;
  "--broker-type" )
      BROKER_TYPE=$2
      shift
      shift
      if [ ! "$BROKER_TYPE" = "kafka" ] && [ ! "$BROKER_TYPE" = "kinesis" ] && [ ! "$BROKER_TYPE" = "gcloud-pubsub" ]; then
        echo >&2 "broker type: '$BROKER_TYPE' not valid. Please supply 'kafka','kinesis' or 'gcloud-pubsub'. Aborting"
        exit 1
      fi
    ;;
  *     )
    echo "Unknown option argument: $1"
    shift
    shift
  ;;
esac
done

# Defaults
DEPLOYMENT_LOCATION=${DEPLOYMENT_LOCATION:-$(mktemp -d || $(echo >&2 "Could not create temp dir" && exit 1))}
MULTISITE_LIB_LOCATION=${MULTISITE_LIB_LOCATION:-${DEF_MULTISITE_LOCATION}}
REPLICATION_LIB_LOCATION=${REPLICATION_LIB_LOCATION:-${DEF_REPLICATION_LOCATION}}
BROKER_TYPE=${BROKER_TYPE:-"kafka"}

# Gerrit primary
GERRIT_1_ETC=${DEPLOYMENT_LOCATION}/etc_1
GERRIT_1_PLUGINS=${DEPLOYMENT_LOCATION}/plugins_1
GERRIT_1_LIBS=${DEPLOYMENT_LOCATION}/libs_1

# Gerrit secondary
GERRIT_2_ETC=${DEPLOYMENT_LOCATION}/etc_2
GERRIT_2_PLUGINS=${DEPLOYMENT_LOCATION}/plugins_2
GERRIT_2_LIBS=${DEPLOYMENT_LOCATION}/libs_2

echo "Deployment location: [${DEPLOYMENT_LOCATION}]"

echo "Downloading common plugins"
COMMON_PLUGINS=${DEPLOYMENT_LOCATION}/common_plugins
mkdir -p ${COMMON_PLUGINS}

cp -f $MULTISITE_LIB_LOCATION $COMMON_PLUGINS/multi-site.jar  >/dev/null 2>&1 || { echo >&2 "$MULTISITE_LIB_LOCATION: Not able to copy the file. Aborting"; exit 1; }
# cp -f $REPLICATION_LIB_LOCATION $COMMON_PLUGINS/replication.jar  >/dev/null 2>&1 || { echo >&2 "$REPLICATION_LIB_LOCATION: Not able to copy the file. Aborting"; exit 1; }

echo "Downloading healthcheck plugin $GERRIT_BRANCH"
wget $GERRIT_CI/plugin-healthcheck-bazel-master-$GERRIT_BRANCH/$LAST_BUILD/healthcheck/healthcheck.jar \
  -O $COMMON_PLUGINS/healthcheck.jar || { echo >&2 "Cannot download healthcheck plugin: Check internet connection. Aborting"; exit 1; }

echo "Downloading zookeeper plugin $GERRIT_BRANCH"
wget $GERRIT_CI/plugin-zookeeper-refdb-bazel-$GERRIT_BRANCH/$LAST_BUILD/zookeeper-refdb/zookeeper-refdb.jar \
  -O $COMMON_PLUGINS/zookeeper-refdb.jar || { echo >&2 "Cannot download zookeeper plugin: Check internet connection. Aborting"; exit 1; }

if [ "$BROKER_TYPE" = "kafka" ]; then
  echo "Downloading events-kafka plugin $GERRIT_BRANCH"
  wget $GERRIT_CI/plugin-events-kafka-bazel-$GERRIT_BRANCH/$LAST_BUILD/events-kafka/events-kafka.jar \
    -O $COMMON_PLUGINS/events-kafka.jar || { echo >&2 "Cannot download events-kafka plugin: Check internet connection. Aborting"; exit 1; }
  BROKER_PORT=9092
  BROKER_HOST=kafka
else
  #TODO add more broker types handling
  echo >&2 "Broker type [${BROKER_TYPE}] not supported. Aborting";
  exit 1;
fi

echo "Downloading common libs"
COMMON_LIBS=${DEPLOYMENT_LOCATION}/common_libs
mkdir -p ${COMMON_LIBS}

cp -f $REPLICATION_LIB_LOCATION $COMMON_LIBS/replication.jar  >/dev/null 2>&1 || { echo >&2 "$REPLICATION_LIB_LOCATION: Not able to copy the file. Aborting"; exit 1; }

echo "Downloading global-refdb library $GERRIT_BRANCH"
wget https://repo1.maven.org/maven2/com/gerritforge/global-refdb/$GLOBAL_REFDB_VER/global-refdb-$GLOBAL_REFDB_VER.jar \
  -O $COMMON_LIBS/global-refdb.jar || { echo >&2 "Cannot download global-refdb library: Check internet connection. Aborting"; exit 1; }

echo "Downloading events-broker library $GERRIT_BRANCH"
wget https://repo1.maven.org/maven2/com/gerritforge/events-broker/$EVENTS_BROKER_VER/events-broker-$EVENTS_BROKER_VER.jar \
  -O $COMMON_LIBS/events-broker.jar || { echo >&2 "Cannot download events-broker library: Check internet connection. Aborting"; exit 1; }

echo "Setting up directories"
mkdir -p ${GERRIT_1_ETC} ${GERRIT_1_PLUGINS} ${GERRIT_1_LIBS} ${GERRIT_2_ETC} ${GERRIT_2_PLUGINS} ${GERRIT_2_LIBS}

echo "Copying plugins"
cp -f $COMMON_PLUGINS/* ${GERRIT_1_PLUGINS}
cp -f $COMMON_PLUGINS/* ${GERRIT_2_PLUGINS}

echo "Copying libs"
cp -f $COMMON_LIBS/* ${GERRIT_1_LIBS}
cp -f $COMMON_PLUGINS/multi-site.jar ${GERRIT_1_LIBS}
cp -f $COMMON_LIBS/* ${GERRIT_2_LIBS}
cp -f $COMMON_PLUGINS/multi-site.jar ${GERRIT_2_LIBS}

echo "Setting up configuration"
echo "Setup healthcheck config"
cp -f ${SCRIPT_DIR}/../setup_local_env/configs/healthcheck.config $GERRIT_1_ETC
cp -f ${SCRIPT_DIR}/../setup_local_env/configs/healthcheck.config $GERRIT_2_ETC

echo "Setup multi-site config"
cp -f ${SCRIPT_DIR}/../setup_local_env/configs/multi-site.config $GERRIT_1_ETC
cp -f ${SCRIPT_DIR}/../setup_local_env/configs/multi-site.config $GERRIT_2_ETC

echo "Setup zookeeper config"
setup_zookeeper_config "${GERRIT_1_ETC}/zookeeper-refdb.config"
setup_zookeeper_config "${GERRIT_2_ETC}/zookeeper-refdb.config"

echo "Setup replication config"
setup_replication_config "${GERRIT_1_ETC}/replication.config" 'file:///var/gerrit/git-instance2/${name}.git'
setup_replication_config "${GERRIT_2_ETC}/replication.config" 'file:///var/gerrit/git-instance1/${name}.git'

echo "Setup gerrit config"
setup_gerrit_config "${GERRIT_1_ETC}/gerrit.config" $BROKER_HOST $BROKER_PORT instance-1 29418
setup_gerrit_config "${GERRIT_2_ETC}/gerrit.config" $BROKER_HOST $BROKER_PORT instance-2 29419

echo "Starting containers"
# store steup in single file (under ${DEPLOYMENT_LOCATION}) with all variables resolved
export GERRIT_1_ETC; \
  export GERRIT_1_PLUGINS; \
  export GERRIT_1_LIBS; \
  export GERRIT_2_ETC; \
  export GERRIT_2_PLUGINS; \
  export GERRIT_2_LIBS; \
  docker-compose -f docker-compose.yaml -f docker-compose-kafka.yaml config > ${DEPLOYMENT_LOCATION}/docker-compose-setup.yaml

docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose-setup.yaml up -d

echo "Installing exit cleanup function"
trap cleanup EXIT

echo "Following logs"
docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose-setup.yaml logs -f
