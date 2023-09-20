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

LOCATION="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
LOCAL_ENV="$( cd "${LOCATION}/../setup_local_env" >/dev/null 2>&1 && pwd )"
GERRIT_BRANCH=stable-3.5
GERRIT_CI=https://archive-ci.gerritforge.com/view/Plugins-$GERRIT_BRANCH/job
LAST_BUILD=lastSuccessfulBuild/artifact/bazel-bin/plugins
DEF_MULTISITE_LOCATION=${LOCATION}/../../../bazel-bin/plugins/multi-site/multi-site.jar
DEF_GERRIT_IMAGE=3.4.0-centos8
DEF_GERRIT_HEALTHCHECK_START_PERIOD=60s
DEF_GERRIT_HEALTHCHECK_INTERVAL=5s
DEF_GERRIT_HEALTHCHECK_TIMEOUT=5s
DEF_GERRIT_HEALTHCHECK_RETRIES=5

function check_application_requirements {
  type java >/dev/null 2>&1 || { echo >&2 "Require java but it's not installed. Aborting."; exit 1; }
  type docker >/dev/null 2>&1 || { echo >&2 "Require docker but it's not installed. Aborting."; exit 1; }
  type docker-compose >/dev/null 2>&1 || { echo >&2 "Require docker-compose but it's not installed. Aborting."; exit 1; }
  type wget >/dev/null 2>&1 || { echo >&2 "Require wget but it's not installed. Aborting."; exit 1; }
  type envsubst >/dev/null 2>&1 || { echo >&2 "Require envsubst but it's not installed. Aborting."; exit 1; }
  type openssl >/dev/null 2>&1 || { echo >&2 "Require openssl but it's not installed. Aborting."; exit 1; }
  type git >/dev/null 2>&1 || { echo >&2 "Require git but it's not installed. Aborting."; exit 1; }
}

function setup_zookeeper_config {
  SOURCE_ZOOKEEPER_CONFIG=${LOCAL_ENV}/configs/zookeeper-refdb.config
  DESTINATION_ZOOKEEPER_CONFIG=$1

  export ZK_HOST=zookeeper
  export ZK_PORT=2181

  echo "Replacing variables for file ${SOURCE_ZOOKEEPER_CONFIG} and copying to ${DESTINATION_ZOOKEEPER_CONFIG}"
  cat $SOURCE_ZOOKEEPER_CONFIG | envsubst | sed 's/#{name}#/${name}/g' > $DESTINATION_ZOOKEEPER_CONFIG
}

function setup_replication_config {
  SOURCE_REPLICATION_CONFIG=${LOCAL_ENV}/configs/replication.config
  DESTINATION_REPLICATION_CONFIG=$1

  export REPLICATION_URL="url = $2"
  export REPLICATION_DELAY_SEC=1

  echo "Replacing variables for file ${SOURCE_REPLICATION_CONFIG} and copying to ${DESTINATION_REPLICATION_CONFIG}"
  cat $SOURCE_REPLICATION_CONFIG | envsubst | sed 's/#{name}#/${name}/g' > $DESTINATION_REPLICATION_CONFIG
}

function setup_gerrit_config {
  SOURCE_RGERRIT_CONFIG=${LOCAL_ENV}/configs/gerrit.config
  DESTINATION_GERRIT_CONFIG=$1

  export BROKER_HOST=$2
  export BROKER_PORT=$3
  export INSTANCE_ID=$4
  export SSH_ADVERTISED_PORT=$5
  export LOCATION_TEST_SITE=/var/gerrit
  export REMOTE_DEBUG_PORT=5005
  export GERRIT_SSHD_PORT=29418
  export HTTP_PROTOCOL=http
  export GERRIT_HTTPD_PORT=8080

  echo "Replacing variables for file ${SOURCE_RGERRIT_CONFIG} and copying to ${DESTINATION_GERRIT_CONFIG}"
  cat $SOURCE_RGERRIT_CONFIG | envsubst | sed 's/#{name}#/${name}/g' > $DESTINATION_GERRIT_CONFIG

  # set plugins for multi-site as mandatory so that gerrit will not start if they are not loaded
  declare -a MANDATORY_PLUGINS=(${BROKER_PLUGIN} multi-site replication websession-broker zookeeper-refdb)
  for plugin in "${MANDATORY_PLUGINS[@]}"
  do
    git config --file $DESTINATION_GERRIT_CONFIG --add plugins.mandatory "${plugin}"
  done
}

function cleanup_tests_hook {
  echo "Shutting down the setup"
  docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml down -v
  echo "Removing setup dir"
  rm -rf ${DEPLOYMENT_LOCATION}
}

function check_result {
  local CONTAINER_ID=$1
  local OLD_RES=$2

  if [ $OLD_RES -ne 0 ]; then
    return $OLD_RES;
  fi

  local RES_VAL=$(docker inspect -f '{{ .State.ExitCode }}' "${CONTAINER_ID}")
  # first check if RES_VAL (output) is a number
  if [[ -z "${RES_VAL##*[!0-9]*}" || $RES_VAL -ne 0 ]]; then
    echo "Tests failed. Here is [docker-compose.yaml] content:"
    cat "${DEPLOYMENT_LOCATION}/docker-compose.yaml"

    echo "Docker logs:"
    cat "${DEPLOYMENT_LOCATION}/site.log"
    return 1
  fi

  return 0
}

# Check application requirements
check_application_requirements

while [ $# -ne 0 ]
do
case "$1" in
  "--help" )
    echo "Usage: sh $0 [--option $value]"
    echo
    echo "[--gerrit-image]                Gerrit docker image to be used for testing; defaults to [${DEF_GERRIT_IMAGE}]"
    echo "[--multisite-lib-file]          Location to lib multi-site.jar file; defaults to [${DEF_MULTISITE_LOCATION}]"
    echo "[--broker-type]                 events broker type; 'kafka', 'kinesis' or 'gcloud-pubsub'. Default 'kafka' TODO: so far only 'kafka'"
    echo "[--start-period]                Gerrit start period timeout (until it gets healthy); defaults to [${DEF_GERRIT_HEALTHCHECK_START_PERIOD}]"
    echo "[--healthcheck-interval]        Every interval Gerrit health check is performed; defaults to [${DEF_GERRIT_HEALTHCHECK_INTERVAL}]"
    echo "[--healthcheck-timeout]         If a single run of the check takes longer than timeout it is considered a failure; defaults to [${DEF_GERRIT_HEALTHCHECK_TIMEOUT}]"
    echo "[--healthcheck-retries]         How many consequtive times health check can fail to consider Gerrit server unhealthy; defaults to [${DEF_GERRIT_HEALTHCHECK_RETRIES}]"
    echo "[--location]                    Directory in which this script resides. Needed only when called from 'bazel test'."
    echo "[--local-env]                   'setup_local_env' directory location. Needed only when called from 'bazel test'."
    echo
    exit 0
  ;;
  "--gerrit-image" )
    GERRIT_IMAGE=$2
    shift
    shift
  ;;
  "--multisite-lib-file" )
    MULTISITE_LIB_LOCATION=$2
    shift
    shift
  ;;
  "--replication-lib-file" )
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
  "--start-period" )
    GERRIT_HEALTHCHECK_START_PERIOD=$2
    shift
    shift
  ;;
  "--healthcheck-interval" )
    GERRIT_HEALTHCHECK_INTERVAL=$2
    shift
    shift
  ;;
  "--healthcheck-timeout" )
    GERRIT_HEALTHCHECK_TIMEOUT=$2
    shift
    shift
  ;;
  "--healthcheck-retries" )
    GERRIT_HEALTHCHECK_RETRIES=$2
    shift
    shift
  ;;
  "--location" )
    LOCATION=$2
    shift
    shift
  ;;
  "--local-env" )
    LOCAL_ENV=$2
    shift
    shift
  ;;
  *     )
    echo "Unknown option argument: $1"
    shift
    shift
  ;;
esac
done

# Defaults
GLOBAL_REFDB_VER=`grep 'com.gerritforge:global-refdb' ${LOCATION}/../external_plugin_deps.bzl | cut -d '"' -f 2 | cut -d ':' -f 3`
DEPLOYMENT_LOCATION=$(mktemp -d || $(echo >&2 "Could not create temp dir" && exit 1))
MULTISITE_LIB_LOCATION=${MULTISITE_LIB_LOCATION:-${DEF_MULTISITE_LOCATION}}
BROKER_TYPE=${BROKER_TYPE:-"kafka"}
GERRIT_IMAGE=${GERRIT_IMAGE:-${DEF_GERRIT_IMAGE}}
GERRIT_HEALTHCHECK_START_PERIOD=${GERRIT_HEALTHCHECK_START_PERIOD:-${DEF_GERRIT_HEALTHCHECK_START_PERIOD}}
GERRIT_HEALTHCHECK_INTERVAL=${GERRIT_HEALTHCHECK_INTERVAL:-${DEF_GERRIT_HEALTHCHECK_INTERVAL}}
GERRIT_HEALTHCHECK_TIMEOUT=${GERRIT_HEALTHCHECK_TIMEOUT:-${DEF_GERRIT_HEALTHCHECK_TIMEOUT}}
GERRIT_HEALTHCHECK_RETRIES=${GERRIT_HEALTHCHECK_RETRIES:-${DEF_GERRIT_HEALTHCHECK_RETRIES}}

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

echo "plugin location[${MULTISITE_LIB_LOCATION}]"
cp -f $MULTISITE_LIB_LOCATION $COMMON_PLUGINS/multi-site.jar  >/dev/null 2>&1 || \
  { echo >&2 "$MULTISITE_LIB_LOCATION: Not able to copy the file. Aborting"; exit 1; }

echo "Downloading websession-broker plugin $GERRIT_BRANCH"
wget $GERRIT_CI/plugin-websession-broker-bazel-$GERRIT_BRANCH/$LAST_BUILD/websession-broker/websession-broker.jar \
  -O $COMMON_PLUGINS/websession-broker.jar || { echo >&2 "Cannot download websession-broker plugin: Check internet connection. Aborting"; exit 1; }

echo "Downloading healthcheck plugin $GERRIT_BRANCH"
wget $GERRIT_CI/plugin-healthcheck-bazel-$GERRIT_BRANCH/$LAST_BUILD/healthcheck/healthcheck.jar \
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
  BROKER_PLUGIN=events-kafka
else
  #TODO add more broker types handling
  echo >&2 "Broker type [${BROKER_TYPE}] not supported. Aborting";
  exit 1;
fi

echo "Downloading common libs"
COMMON_LIBS=${DEPLOYMENT_LOCATION}/common_libs
mkdir -p ${COMMON_LIBS}

echo "Getting replication.jar as a library"
CONTAINER_NAME=$(docker create -ti --entrypoint /bin/bash gerritcodereview/gerrit:"${GERRIT_IMAGE}") && \
docker cp ${CONTAINER_NAME}:/var/gerrit/plugins/replication.jar $COMMON_LIBS/
docker rm -fv ${CONTAINER_NAME}

echo "Downloading global-refdb library $GERRIT_BRANCH"
wget https://repo1.maven.org/maven2/com/gerritforge/global-refdb/$GLOBAL_REFDB_VER/global-refdb-$GLOBAL_REFDB_VER.jar \
  -O $COMMON_LIBS/global-refdb.jar || { echo >&2 "Cannot download global-refdb library: Check internet connection. Aborting"; exit 1; }

echo "Downloading events-broker library $GERRIT_BRANCH"
cp bazel-bin/plugins/events-broker/events-broker.jar $COMMON_LIBS/events-broker.jar

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
cp -f ${LOCAL_ENV}/configs/healthcheck.config $GERRIT_1_ETC
cp -f ${LOCAL_ENV}/configs/healthcheck.config $GERRIT_2_ETC

echo "Setup multi-site config"
cp -f ${LOCAL_ENV}/configs/multi-site.config $GERRIT_1_ETC
cp -f ${LOCAL_ENV}/configs/multi-site.config $GERRIT_2_ETC

echo "Setup zookeeper config"
setup_zookeeper_config "${GERRIT_1_ETC}/zookeeper-refdb.config"
setup_zookeeper_config "${GERRIT_2_ETC}/zookeeper-refdb.config"

echo "Setup replication config"
setup_replication_config "${GERRIT_1_ETC}/replication.config" 'file:///var/gerrit/git-instance2/${name}.git'
setup_replication_config "${GERRIT_2_ETC}/replication.config" 'file:///var/gerrit/git-instance1/${name}.git'

echo "Setup gerrit config"
setup_gerrit_config "${GERRIT_1_ETC}/gerrit.config" $BROKER_HOST $BROKER_PORT instance-1 29418
setup_gerrit_config "${GERRIT_2_ETC}/gerrit.config" $BROKER_HOST $BROKER_PORT instance-2 29419

echo "Generating common SSH key for tests"
COMMON_SSH=${DEPLOYMENT_LOCATION}/common_ssh
mkdir -p ${COMMON_SSH}
ssh-keygen -b 2048 -t rsa -f ${COMMON_SSH}/id_rsa -q -N "" || { echo >&2 "Cannot generate common SSH keys. Aborting"; exit 1; }

SCENARIOS="$( cd "${LOCATION}" >/dev/null 2>&1 && pwd )/scenarios.sh"

echo "Starting containers"
COMPOSE_FILES="-f ${LOCATION}/docker-compose.yaml -f ${LOCATION}/docker-compose-kafka.yaml -f ${LOCATION}/docker-tester.yaml"

# store setup in single file (under ${DEPLOYMENT_LOCATION}) with all variables resolved
export GERRIT_IMAGE; \
  export GERRIT_HEALTHCHECK_START_PERIOD; \
  export GERRIT_HEALTHCHECK_INTERVAL; \
  export GERRIT_HEALTHCHECK_TIMEOUT; \
  export GERRIT_HEALTHCHECK_RETRIES; \
  docker-compose ${COMPOSE_FILES} config > ${DEPLOYMENT_LOCATION}/docker-compose.yaml

trap cleanup_tests_hook EXIT
docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml up -d zookeeper kafka
docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml ps -a
docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml logs -f --no-color -t > ${DEPLOYMENT_LOCATION}/site.log &

docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml up --no-start gerrit1 gerrit2
docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml ps -a
GERRIT1_CONTAINER=$(docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml ps -q gerrit1)
GERRIT2_CONTAINER=$(docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml ps -q gerrit2)

#copy files to gerrit containers
echo "Copying files to Gerrit containers"
docker cp "${GERRIT_1_ETC}/" "${GERRIT1_CONTAINER}:/var/gerrit/etc"
docker cp "${GERRIT_1_PLUGINS}/" "${GERRIT1_CONTAINER}:/var/gerrit/plugins"
docker cp "${GERRIT_1_LIBS}/" "${GERRIT1_CONTAINER}:/var/gerrit/libs"
docker cp "${COMMON_SSH}/" "${GERRIT1_CONTAINER}:/var/gerrit/.ssh"

docker cp "${GERRIT_2_ETC}/" "${GERRIT2_CONTAINER}:/var/gerrit/etc"
docker cp "${GERRIT_2_PLUGINS}/" "${GERRIT2_CONTAINER}:/var/gerrit/plugins"
docker cp "${GERRIT_2_LIBS}/" "${GERRIT2_CONTAINER}:/var/gerrit/libs"
docker cp "${COMMON_SSH}/" "${GERRIT2_CONTAINER}:/var/gerrit/.ssh"

echo "Starting Gerrit servers"
docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml up -d gerrit1 gerrit2

echo "Waiting for services to start (and being healthy) and calling e2e tests"
docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml up --no-start tester
docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml ps -a
TEST_CONTAINER=$(docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml ps -q tester)
docker cp "${COMMON_SSH}/" "${TEST_CONTAINER}:/var/gerrit/.ssh"
docker cp "${SCENARIOS}" "${TEST_CONTAINER}:/var/gerrit/scenarios.sh"

docker-compose -f ${DEPLOYMENT_LOCATION}/docker-compose.yaml up tester

# inspect test container exit code as 'up' always returns '0'
check_result "${TEST_CONTAINER}" 0
RES_VAL=$?
check_result "${GERRIT1_CONTAINER}" ${RES_VAL}
RES_VAL=$?
check_result "${GERRIT2_CONTAINER}" ${RES_VAL}
RES_VAL=$?

exit $RES_VAL
