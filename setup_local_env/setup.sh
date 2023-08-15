#!/bin/bash

# Copyright (C) 2019 The Android Open Source Project
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
GERRIT_CI=https://archive-ci.gerritforge.com/view/Plugins-$GERRIT_BRANCH/job
LAST_BUILD=lastSuccessfulBuild/artifact/bazel-bin/plugins
EVENTS_BROKER_VER=`grep 'com.gerritforge:events-broker' $(dirname $0)/../external_plugin_deps.bzl | cut -d '"' -f 2 | cut -d ':' -f 3`
GLOBAL_REFDB_VER=`grep 'com.gerritforge:global-refdb' $(dirname $0)/../external_plugin_deps.bzl | cut -d '"' -f 2 | cut -d ':' -f 3`

function check_application_requirements {
  type haproxy >/dev/null 2>&1 || { echo >&2 "Require haproxy but it's not installed. Aborting."; exit 1; }
  type java >/dev/null 2>&1 || { echo >&2 "Require java but it's not installed. Aborting."; exit 1; }
  type docker >/dev/null 2>&1 || { echo >&2 "Require docker but it's not installed. Aborting."; exit 1; }
  [ $($SUDO docker --version | awk '{print $3}' | cut -d '.' -f 1) -ge 20 ] || { echo >&2 "Require docker v20 or later. Aborting."; exit 1; }
  type wget >/dev/null 2>&1 || { echo >&2 "Require wget but it's not installed. Aborting."; exit 1; }
  type envsubst >/dev/null 2>&1 || { echo >&2 "Require envsubst but it's not installed. Aborting."; exit 1; }
  type openssl >/dev/null 2>&1 || { echo >&2 "Require openssl but it's not installed. Aborting."; exit 1; }
  if [ "$BROKER_TYPE" = "kinesis" ];  then
    type aws >/dev/null 2>&1 || { echo >&2 "Require aws-cli but it's not installed. Aborting."; exit 1; }
  fi
}

function get_pull_replication_api_url {
  REPLICATION_HOSTNAME=$1

  echo "apiUrl = http://$REPLICATION_HOSTNAME:$REPLICATION_HTTPD_PORT"
}

function get_pull_replication_url {
  REPLICATION_HOSTNAME=$1

  echo "url = http://$REPLICATION_HOSTNAME:$REPLICATION_HTTPD_PORT/#{name}#.git"
}

function deploy_tls_certificates {
  echo "Deplying certificates in $HA_PROXY_CERTIFICATES_DIR..."
  openssl req -new -newkey rsa:2048 -x509 -sha256 -days 365 -nodes \
  -out $HA_PROXY_CERTIFICATES_DIR/MyCertificate.crt \
  -keyout $HA_PROXY_CERTIFICATES_DIR/GerritLocalKey.key \
  -subj "/C=GB/ST=London/L=London/O=Gerrit Org/OU=IT Department/CN=localhost"
  cat $HA_PROXY_CERTIFICATES_DIR/MyCertificate.crt $HA_PROXY_CERTIFICATES_DIR/GerritLocalKey.key | tee $HA_PROXY_CERTIFICATES_DIR/GerritLocalKey.pem
}

function copy_config_files {
  for file in `ls $SCRIPT_DIR/configs/*.config`
  do
    file_name=`basename $file`

    CONFIG_TEST_SITE=$1
    export GERRIT_HTTPD_PORT=$2
    export LOCATION_TEST_SITE=$3
    export GERRIT_SSHD_PORT=$4
    export REPLICATION_HTTPD_PORT=$5
    export GERRIT_HOSTNAME=$6
    export REPLICATION_HOSTNAME=$7
    export REMOTE_DEBUG_PORT=$8
    export INSTANCE_ID=${9}
    export REPLICA_INSTANCE_ID=${10}
    export PULL_REPLICATION_URL=$(get_pull_replication_url $REPLICATION_HOSTNAME)
    export PULL_REPLICATION_API_URL=$(get_pull_replication_api_url $REPLICATION_HOSTNAME)

    echo "Replacing variables for file $file and copying to $CONFIG_TEST_SITE/$file_name"

    cat $file | envsubst | sed 's/#{name}#/${name}/g' > $CONFIG_TEST_SITE/$file_name
  done
}

function start_ha_proxy {

  export HA_GERRIT_CANONICAL_HOSTNAME=$GERRIT_CANONICAL_HOSTNAME
  export HA_GERRIT_CANONICAL_PORT=$GERRIT_CANONICAL_PORT

  export HA_HTTPS_BIND=$HTTPS_BIND

  export HA_GERRIT_SITE1_HOSTNAME=$GERRIT_1_HOSTNAME
  export HA_GERRIT_SITE2_HOSTNAME=$GERRIT_2_HOSTNAME
  export HA_GERRIT_SITE1_HTTPD_PORT=$GERRIT_1_HTTPD_PORT
  export HA_GERRIT_SITE2_HTTPD_PORT=$GERRIT_2_HTTPD_PORT

  export HA_GERRIT_SITE1_SSHD_PORT=$GERRIT_1_SSHD_PORT
  export HA_GERRIT_SITE2_SSHD_PORT=$GERRIT_2_SSHD_PORT

  cat $SCRIPT_DIR/haproxy-config/haproxy.cfg | envsubst > $HA_PROXY_CONFIG_DIR/haproxy.cfg

  echo "Starting HA-PROXY..."
  echo "THE SCRIPT LOCATION $SCRIPT_DIR"
  echo "THE HA SCRIPT_LOCATION $HA_SCRIPT_DIR"
  haproxy -f $HA_PROXY_CONFIG_DIR/haproxy.cfg &
}

function export_broker_port {
  if [ "$BROKER_TYPE" = "kinesis" ]; then
    export BROKER_PORT=4566
  elif [ "$BROKER_TYPE" =  "kafka" ]; then
    export BROKER_PORT=9092
  elif [ "$BROKER_TYPE" =  "gcloud-pubsub" ]; then
    export BROKER_PORT=8085
  fi
}

function deploy_config_files {
  # broker configuration
  export BROKER_HOST=localhost

  # ZK configuration
  export ZK_HOST=localhost
  export ZK_PORT=2181

  # SITE 1
  GERRIT_SITE1_HOSTNAME=$1
  GERRIT_SITE1_HTTPD_PORT=$2
  GERRIT_SITE1_SSHD_PORT=$3
  CONFIG_TEST_SITE_1=$LOCATION_TEST_SITE_1/etc
  GERRIT_SITE1_REMOTE_DEBUG_PORT="5005"
  GERRIT_SITE1_INSTANCE_ID="instance-1"
  # SITE 2
  GERRIT_SITE2_HOSTNAME=$4
  GERRIT_SITE2_HTTPD_PORT=$5
  GERRIT_SITE2_SSHD_PORT=$6
  CONFIG_TEST_SITE_2=$LOCATION_TEST_SITE_2/etc
  GERRIT_SITE2_REMOTE_DEBUG_PORT="5006"
  GERRIT_SITE2_INSTANCE_ID="instance-2"

  # Set config SITE1
  copy_config_files $CONFIG_TEST_SITE_1 $GERRIT_SITE1_HTTPD_PORT $LOCATION_TEST_SITE_1 $GERRIT_SITE1_SSHD_PORT $GERRIT_SITE2_HTTPD_PORT $GERRIT_SITE1_HOSTNAME $GERRIT_SITE2_HOSTNAME $GERRIT_SITE1_REMOTE_DEBUG_PORT $GERRIT_SITE1_INSTANCE_ID $GERRIT_SITE2_INSTANCE_ID


  # Set config SITE2
  copy_config_files $CONFIG_TEST_SITE_2 $GERRIT_SITE2_HTTPD_PORT $LOCATION_TEST_SITE_2 $GERRIT_SITE2_SSHD_PORT $GERRIT_SITE1_HTTPD_PORT $GERRIT_SITE1_HOSTNAME $GERRIT_SITE2_HOSTNAME $GERRIT_SITE2_REMOTE_DEBUG_PORT $GERRIT_SITE2_INSTANCE_ID $GERRIT_SITE1_INSTANCE_ID
}

function is_docker_desktop {
  echo $($SUDO docker info | grep "Operating System: Docker Desktop" | wc -l)
}

function docker_host_env {
  IS_DOCKER_DESKTOP=$(is_docker_desktop)
  if [ "$IS_DOCKER_DESKTOP" = "1" ];then
    echo "mac"
  else
    echo "linux"
  fi
}

function create_kinesis_streams {
  for stream in "gerrit_batch_index" "gerrit_cache_eviction" "gerrit_index" "gerrit_list_project" "gerrit_stream" "gerrit_web_session" "gerrit"
  do
    create_kinesis_stream $stream
  done
}

function create_kinesis_stream {
  local stream=$1

  export AWS_PAGER=''
  export AWS_ACCESS_KEY_ID=accessKey
  export AWS_SECRET_ACCESS_KEY=secretKey
  export AWS_DEFAULT_REGION=us-east-1
  echo "[KINESIS] Create stream $stream"
  until aws --endpoint-url=http://localhost:$BROKER_PORT kinesis create-stream --shard-count 1 --stream-name "$stream"
  do
      echo "[KINESIS stream $stream] Creation failed. Retrying in 5 seconds..."
      sleep 5s
  done
}

function cleanup_environment {
  echo "Killing existing HA-PROXY setup"
  kill $(ps -ax | grep haproxy | grep "gerrit_setup/ha-proxy-config" | awk '{print $1}') 2> /dev/null

  echo "Stopping $BROKER_TYPE docker container"
  printenv > "${SCRIPT_DIR}/docker-compose-${BROKER_TYPE}.env"
  $SUDO docker compose -f "${SCRIPT_DIR}/docker-compose-${BROKER_TYPE}.yaml" --env-file "${SCRIPT_DIR}/docker-compose-${BROKER_TYPE}.env" down 2> /dev/null
  echo "Stopping core docker containers"
  $SUDO docker compose -f "${SCRIPT_DIR}/docker-compose-core.yaml" --env-file "${SCRIPT_DIR}/docker-compose-${BROKER_TYPE}.env" down 2> /dev/null

  echo "Stopping GERRIT instances"
  $1/bin/gerrit.sh stop 2> /dev/null
  $2/bin/gerrit.sh stop 2> /dev/null

  echo "REMOVING setup directory $3"
  rm -rf $3 2> /dev/null
}

function check_if_container_is_running {
  local container=$1;
  echo $($SUDO docker inspect "$container" 2> /dev/null | grep '"Running": true' | wc -l)
}

function ensure_docker_compose_is_up_and_running {
  local log_label=$1
  local container_name=$2
  local docker_compose_file=$3

  local is_container_running=$(check_if_container_is_running "$container_name")
  if [ "$is_container_running" -lt 1 ];then
    printenv > "${SCRIPT_DIR}/${docker_compose_file}.env"
    echo "[$log_label] Starting docker containers"
    $SUDO docker compose -f "${SCRIPT_DIR}/${docker_compose_file}" --env-file "${SCRIPT_DIR}/${docker_compose_file}.env" up -d

    echo "[$log_label] Waiting for docker containers to start..."
    while [[ $(check_if_container_is_running "$container_name") -lt 1 ]];do sleep 10s; done
  else
    echo "[$log_label] Containers already running, nothing to do"
  fi
}

function prepare_broker_data {
  if [ "$BROKER_TYPE" = "kinesis" ]; then
    create_kinesis_streams
  fi
}

while [ $# -ne 0 ]
do
case "$1" in
  "--help" )
    echo "Usage: sh $0 [--option $value]"
    echo
    echo "[--release-war-file]            Location to release.war file"
    echo "[--multisite-lib-file]          Location to lib multi-site.jar file"
    echo
    echo "[--new-deployment]              Cleans up previous gerrit deployment and re-installs it. default true"
    echo "[--get-websession-plugin]       Download websession-broker plugin from CI lastSuccessfulBuild; default true"
    echo "[--deployment-location]         Base location for the test deployment; default /tmp"
    echo
    echo "[--gerrit-canonical-host]       The default host for Gerrit to be accessed through; default localhost"
    echo "[--gerrit-canonical-port]       The default port for Gerrit to be accessed throug; default 8080"
    echo
    echo "[--gerrit-ssh-advertised-port]  Gerrit Instance 1 sshd port; default 29418"
    echo
    echo "[--gerrit1-httpd-port]          Gerrit Instance 1 http port; default 18080"
    echo "[--gerrit1-sshd-port]           Gerrit Instance 1 sshd port; default 39418"
    echo
    echo "[--gerrit2-httpd-port]          Gerrit Instance 2 http port; default 18081"
    echo "[--gerrit2-sshd-port]           Gerrit Instance 2 sshd port; default 49418"
    echo
    echo "[--replication-delay]           Replication delay across the two instances in seconds"
    echo
    echo "[--just-cleanup-env]            Cleans up previous deployment; default false"
    echo
    echo "[--enabled-https]               Enabled https; default true"
    echo
    echo "[--broker-type]                 events broker type; 'kafka', 'kinesis' or 'gcloud-pubsub'. Default 'kafka'"
    echo
    echo "[--sudo]                        run docker commands with sudo"
    echo
    exit 0
  ;;
  "--new-deployment")
        NEW_INSTALLATION=$2
    shift
    shift
  ;;
  "--get-websession-plugin")
    DOWNLOAD_WEBSESSION_PLUGIN=$2
    shift
    shift
  ;;
  "--deployment-location" )
    DEPLOYMENT_LOCATION=$2
    shift
    shift
  ;;
  "--release-war-file" )
    RELEASE_WAR_FILE_LOCATION=$2
    shift
    shift
  ;;
  "--multisite-lib-file" )
    MULTISITE_LIB_LOCATION=$2
    shift
    shift
  ;;
  "--gerrit-canonical-host" )
    export GERRIT_CANONICAL_HOSTNAME=$2
    shift
    shift
  ;;
  "--gerrit-canonical-port" )
    export GERRIT_CANONICAL_PORT=$2
    shift
    shift
  ;;
  "--gerrit-ssh-advertised-port" )
    export SSH_ADVERTISED_PORT=$2
    shift
    shift
  ;;
  "--gerrit1-httpd-port" )
         GERRIT_1_HTTPD_PORT=$2
    shift
    shift
  ;;
  "--gerrit2-httpd-port" )
         GERRIT_2_HTTPD_PORT=$2
    shift
    shift
  ;;
  "--gerrit1-sshd-port" )
         GERRIT_1_SSHD_PORT=$2
    shift
    shift
  ;;
  "--gerrit2-sshd-port" )
         GERRIT_2_SSHD_PORT=$2
    shift
    shift
  ;;
  "--replication-delay")
    export REPLICATION_DELAY_SEC=$2
    shift
    shift
  ;;
  "--just-cleanup-env" )
         JUST_CLEANUP_ENV=$2
    shift
    shift
  ;;
  "--enabled-https" )
    HTTPS_ENABLED=$2
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
  "--sudo" )
    SUDO=sudo
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

# Check application requirements
check_application_requirements

# Defaults
NEW_INSTALLATION=${NEW_INSTALLATION:-"true"}
DOWNLOAD_WEBSESSION_PLUGIN=${DOWNLOAD_WEBSESSION_PLUGIN:-"true"}
DEPLOYMENT_LOCATION=${DEPLOYMENT_LOCATION:-"/tmp"}
export GERRIT_CANONICAL_HOSTNAME=${GERRIT_CANONICAL_HOSTNAME:-"localhost"}
export GERRIT_CANONICAL_PORT=${GERRIT_CANONICAL_PORT:-"8080"}
GERRIT_1_HOSTNAME=${GERRIT_1_HOSTNAME:-"localhost"}
GERRIT_2_HOSTNAME=${GERRIT_2_HOSTNAME:-"localhost"}
GERRIT_1_HTTPD_PORT=${GERRIT_1_HTTPD_PORT:-"18080"}
GERRIT_2_HTTPD_PORT=${GERRIT_2_HTTPD_PORT:-"18081"}
GERRIT_1_SSHD_PORT=${GERRIT_1_SSHD_PORT:-"39418"}
GERRIT_2_SSHD_PORT=${GERRIT_2_SSHD_PORT:-"49418"}
export REPLICATION_DELAY_SEC=${REPLICATION_DELAY_SEC:-"5"}
export SSH_ADVERTISED_PORT=${SSH_ADVERTISED_PORT:-"29418"}
HTTPS_ENABLED=${HTTPS_ENABLED:-"false"}
BROKER_TYPE=${BROKER_TYPE:-"kafka"}

export COMMON_LOCATION=$DEPLOYMENT_LOCATION/gerrit_setup
LOCATION_TEST_SITE_1=$COMMON_LOCATION/instance-1
LOCATION_TEST_SITE_2=$COMMON_LOCATION/instance-2
HA_PROXY_CONFIG_DIR=$COMMON_LOCATION/ha-proxy-config
HA_PROXY_CERTIFICATES_DIR="$HA_PROXY_CONFIG_DIR/certificates"
PROMETHEUS_CONFIG_DIR=$COMMON_LOCATION/prometheus-config

RELEASE_WAR_FILE_LOCATION=${RELEASE_WAR_FILE_LOCATION:-bazel-bin/release.war}
MULTISITE_LIB_LOCATION=${MULTISITE_LIB_LOCATION:-bazel-bin/plugins/multi-site/multi-site.jar}


export FAKE_NFS=$COMMON_LOCATION/fake_nfs

if [ "$JUST_CLEANUP_ENV" = "true" ];then
  cleanup_environment $LOCATION_TEST_SITE_1 $LOCATION_TEST_SITE_2 $COMMON_LOCATION
  exit 0
fi

if [ -z $RELEASE_WAR_FILE_LOCATION ];then
  echo "A release.war file is required. Usage: sh $0 --release-war-file /path/to/release.war"
  exit 1
else
  cp -f $RELEASE_WAR_FILE_LOCATION $DEPLOYMENT_LOCATION/gerrit.war >/dev/null 2>&1 || { echo >&2 "$RELEASE_WAR_FILE_LOCATION: Not able to copy the file. Aborting"; exit 1; }
fi
if [ -z $MULTISITE_LIB_LOCATION ];then
  echo "The multi-site library is required. Usage: sh $0 --multisite-lib-file /path/to/multi-site.jar"
  exit 1
else
  cp -f $MULTISITE_LIB_LOCATION $DEPLOYMENT_LOCATION/multi-site.jar  >/dev/null 2>&1 || { echo >&2 "$MULTISITE_LIB_LOCATION: Not able to copy the file. Aborting"; exit 1; }
fi
if [ $DOWNLOAD_WEBSESSION_PLUGIN = "true" ];then
  echo "Downloading websession-broker plugin $GERRIT_BRANCH"
  wget $GERRIT_CI/plugin-websession-broker-bazel-$GERRIT_BRANCH/$LAST_BUILD/websession-broker/websession-broker.jar \
  -O $DEPLOYMENT_LOCATION/websession-broker.jar || \
  wget $GERRIT_CI/plugin-websession-broker-bazel-master-$GERRIT_BRANCH/$LAST_BUILD/websession-broker/websession-broker.jar \
  -O $DEPLOYMENT_LOCATION/websession-broker.jar || \
  { echo >&2 "Cannot download websession-broker plugin: Check internet connection. Abort\
ing"; exit 1; }
  wget $GERRIT_CI/plugin-healthcheck-bazel-$GERRIT_BRANCH/$LAST_BUILD/healthcheck/healthcheck.jar \
  -O $DEPLOYMENT_LOCATION/healthcheck.jar || { echo >&2 "Cannot download healthcheck plugin: Check internet connection. Abort\
ing"; exit 1; }
else
  echo "Without the websession-broker; user login via haproxy will fail."
fi

echo "Downloading zookeeper plugin $GERRIT_BRANCH"
  wget $GERRIT_CI/plugin-zookeeper-refdb-bazel-$GERRIT_BRANCH/$LAST_BUILD/zookeeper-refdb/zookeeper-refdb.jar \
  -O $DEPLOYMENT_LOCATION/zookeeper-refdb.jar || \
  wget $GERRIT_CI/plugin-zookeeper-refdb-bazel-master-$GERRIT_BRANCH/$LAST_BUILD/zookeeper-refdb/zookeeper-refdb.jar \
  -O $DEPLOYMENT_LOCATION/zookeeper-refdb.jar || \
  { echo >&2 "Cannot download zookeeper plugin: Check internet connection. Abort\
ing"; exit 1; }

echo "Downloading global-refdb library $GERRIT_BRANCH"
  wget https://repo1.maven.org/maven2/com/gerritforge/global-refdb/$GLOBAL_REFDB_VER/global-refdb-$GLOBAL_REFDB_VER.jar \
  -O $DEPLOYMENT_LOCATION/global-refdb.jar || { echo >&2 "Cannot download global-refdb library: Check internet connection. Abort\
ing"; exit 1; }

echo "Downloading events-broker library $GERRIT_BRANCH"
  wget https://repo1.maven.org/maven2/com/gerritforge/events-broker/$EVENTS_BROKER_VER/events-broker-$EVENTS_BROKER_VER.jar \
  -O $DEPLOYMENT_LOCATION/events-broker.jar || { echo >&2 "Cannot download events-broker library: Check internet connection. Abort\
ing"; exit 1; }

if [ "$BROKER_TYPE" = "kafka" ]; then
echo "Downloading events-kafka plugin $GERRIT_BRANCH"
  wget $GERRIT_CI/plugin-events-kafka-bazel-$GERRIT_BRANCH/$LAST_BUILD/events-kafka/events-kafka.jar \
  -O $DEPLOYMENT_LOCATION/events-kafka.jar || \
  wget $GERRIT_CI/plugin-events-kafka-bazel-master-$GERRIT_BRANCH/$LAST_BUILD/events-kafka/events-kafka.jar \
  -O $DEPLOYMENT_LOCATION/events-kafka.jar || \
  { echo >&2 "Cannot download events-kafka plugin: Check internet connection. Abort\
ing"; exit 1; }
fi

if [ "$BROKER_TYPE" = "kinesis" ]; then
echo "Downloading events-aws-kinesis plugin $GERRIT_BRANCH"
  wget $GERRIT_CI/plugin-events-aws-kinesis-bazel-$GERRIT_BRANCH/$LAST_BUILD/events-aws-kinesis/events-aws-kinesis.jar \
  -O $DEPLOYMENT_LOCATION/events-aws-kinesis.jar || \
  wget $GERRIT_CI/plugin-events-aws-kinesis-bazel-master-$GERRIT_BRANCH/$LAST_BUILD/events-aws-kinesis/events-aws-kinesis.jar \
  -O $DEPLOYMENT_LOCATION/events-aws-kinesis.jar || \
  { echo >&2 "Cannot download events-aws-kinesis plugin: Check internet connection. Abort\
ing"; exit 1; }
fi


if [ "$BROKER_TYPE" = "gcloud-pubsub" ]; then
echo "Downloading events-gcloud-pubsub plugin $GERRIT_BRANCH"
  wget $GERRIT_CI/plugin-events-gcloud-pubsub-bazel-$GERRIT_BRANCH/$LAST_BUILD/events-gcloud-pubsub/events-gcloud-pubsub.jar \
  -O $DEPLOYMENT_LOCATION/events-gcloud-pubsub.jar || \
  wget $GERRIT_CI/plugin-events-gcloud-pubsub-bazel-master-$GERRIT_BRANCH/$LAST_BUILD/events-gcloud-pubsub/events-gcloud-pubsub.jar \
  -O $DEPLOYMENT_LOCATION/events-gcloud-pubsub.jar || \
  { echo >&2 "Cannot download events-gcloud-pubsub plugin: Check internet connection. Abort\
ing"; exit 1; }
fi

echo "Downloading metrics-reporter-prometheus plugin $GERRIT_BRANCH"
  wget $GERRIT_CI/plugin-metrics-reporter-prometheus-bazel-$GERRIT_BRANCH/$LAST_BUILD/metrics-reporter-prometheus/metrics-reporter-prometheus.jar \
  -O $DEPLOYMENT_LOCATION/metrics-reporter-prometheus.jar || \
  wget $GERRIT_CI/plugin-metrics-reporter-prometheus-bazel-master-$GERRIT_BRANCH/$LAST_BUILD/metrics-reporter-prometheus/metrics-reporter-prometheus.jar \
  -O $DEPLOYMENT_LOCATION/metrics-reporter-prometheus.jar || \
  { echo >&2 "Cannot download metrics-reporter-prometheus plugin: Check internet connection. Abort\
ing"; exit 1; }

echo "Downloading pull-replication plugin $GERRIT_BRANCH"
  wget $GERRIT_CI/plugin-pull-replication-bazel-$GERRIT_BRANCH/$LAST_BUILD/pull-replication/pull-replication.jar \
  -O $DEPLOYMENT_LOCATION/pull-replication.jar || { echo >&2 "Cannot download pull-replication plugin: Check internet connection. Abort\
ing"; exit 1; }

if [ "$HTTPS_ENABLED" = "true" ];then
  export HTTP_PROTOCOL="https"
  export GERRIT_CANONICAL_WEB_URL="$HTTP_PROTOCOL://$GERRIT_CANONICAL_HOSTNAME/"
  export HTTPS_BIND="bind *:443 ssl crt $HA_PROXY_CONFIG_DIR/certificates/GerritLocalKey.pem"
  HTTPS_CLONE_MSG="Using self-signed certificates, to clone via https - 'git config --global http.sslVerify false'"
else
  export HTTP_PROTOCOL="http"
  export GERRIT_CANONICAL_WEB_URL="$HTTP_PROTOCOL://$GERRIT_CANONICAL_HOSTNAME:$GERRIT_CANONICAL_PORT/"
fi

# New installation
if [ $NEW_INSTALLATION = "true" ]; then

  cleanup_environment $LOCATION_TEST_SITE_1 $LOCATION_TEST_SITE_2 $COMMON_LOCATION

  echo "Setting up directories"
  mkdir -p $LOCATION_TEST_SITE_1 $LOCATION_TEST_SITE_2 $HA_PROXY_CERTIFICATES_DIR $FAKE_NFS
  java -jar $DEPLOYMENT_LOCATION/gerrit.war init --batch --no-auto-start --install-all-plugins --dev -d $LOCATION_TEST_SITE_1

  # Deploying TLS certificates
  if [ "$HTTPS_ENABLED" = "true" ];then deploy_tls_certificates;fi

  echo "Copy multi-site library to lib directory"
  cp -f $DEPLOYMENT_LOCATION/multi-site.jar $LOCATION_TEST_SITE_1/lib/multi-site.jar

  echo "Copy websession-broker plugin"
  cp -f $DEPLOYMENT_LOCATION/websession-broker.jar $LOCATION_TEST_SITE_1/plugins/websession-broker.jar

  echo "Copy healthcheck plugin"
  cp -f $DEPLOYMENT_LOCATION/healthcheck.jar $LOCATION_TEST_SITE_1/plugins/healthcheck.jar

  echo "Copy zookeeper plugin"
  cp -f $DEPLOYMENT_LOCATION/zookeeper-refdb.jar $LOCATION_TEST_SITE_1/plugins/zookeeper-refdb.jar

  echo "Copy global refdb library"
  cp -f $DEPLOYMENT_LOCATION/global-refdb.jar $LOCATION_TEST_SITE_1/lib/global-refdb.jar

  echo "Copy events broker library"
  cp -f $DEPLOYMENT_LOCATION/events-broker.jar $LOCATION_TEST_SITE_1/lib/events-broker.jar

  echo "Copy $BROKER_TYPE events plugin"
  if [ $BROKER_TYPE = "kinesis" ]; then
     cp -f $DEPLOYMENT_LOCATION/events-aws-kinesis.jar $LOCATION_TEST_SITE_1/plugins/events-aws-kinesis.jar
  elif [ $BROKER_TYPE = "gcloud-pubsub" ]; then
    cp -f $DEPLOYMENT_LOCATION/events-gcloud-pubsub.jar $LOCATION_TEST_SITE_1/plugins/events-gcloud-pubsub.jar
  else
    cp -f $DEPLOYMENT_LOCATION/events-kafka.jar $LOCATION_TEST_SITE_1/plugins/events-kafka.jar
  fi

  echo "Copy metrics-reporter-prometheus plugin"
  cp -f $DEPLOYMENT_LOCATION/metrics-reporter-prometheus.jar $LOCATION_TEST_SITE_1/plugins/metrics-reporter-prometheus.jar

  echo "Re-indexing"
  java -jar $DEPLOYMENT_LOCATION/gerrit.war reindex -d $LOCATION_TEST_SITE_1
  # Replicating environment
  echo "Replicating environment"
  cp -fR $LOCATION_TEST_SITE_1/* $LOCATION_TEST_SITE_2

  echo "Link replication plugin"
  ln -s $LOCATION_TEST_SITE_1/plugins/replication.jar $LOCATION_TEST_SITE_1/lib/replication.jar
  ln -s $LOCATION_TEST_SITE_2/plugins/replication.jar $LOCATION_TEST_SITE_2/lib/replication.jar

  echo "Link pullreplication plugin"
  ln -s $LOCATION_TEST_SITE_1/plugins/pull-replication.jar $LOCATION_TEST_SITE_1/lib/pull-replication.jar
  ln -s $LOCATION_TEST_SITE_2/plugins/pull-replication.jar $LOCATION_TEST_SITE_2/lib/pull-replication.jar

  echo "Link multi-site library to plugin directory"
  ln -s $LOCATION_TEST_SITE_1/lib/multi-site.jar $LOCATION_TEST_SITE_1/plugins/multi-site.jar
  ln -s $LOCATION_TEST_SITE_2/lib/multi-site.jar $LOCATION_TEST_SITE_2/plugins/multi-site.jar

  echo "Copy pull-replication plugin"
  cp -f $DEPLOYMENT_LOCATION/pull-replication.jar $LOCATION_TEST_SITE_1/plugins/pull-replication.jar
  cp -f $DEPLOYMENT_LOCATION/pull-replication.jar $LOCATION_TEST_SITE_2/plugins/pull-replication.jar

fi

DOCKER_HOST_ENV=$(docker_host_env)
echo "Docker host environment: $DOCKER_HOST_ENV"
if [ "$DOCKER_HOST_ENV" = "mac" ];then
  export GERRIT_SITE_HOST="host.docker.internal"
  export NETWORK_MODE="bridge"
else
  export GERRIT_SITE_HOST="localhost"
  export NETWORK_MODE="host"
fi

cat $SCRIPT_DIR/configs/prometheus.yml | envsubst > $COMMON_LOCATION/prometheus.yml

export_broker_port
ensure_docker_compose_is_up_and_running "core" "prometheus_test_node" "docker-compose-core.yaml"
ensure_docker_compose_is_up_and_running "$BROKER_TYPE" "${BROKER_TYPE}_test_node" "docker-compose-$BROKER_TYPE.yaml"
prepare_broker_data

echo "Re-deploying configuration files"
deploy_config_files $GERRIT_1_HOSTNAME $GERRIT_1_HTTPD_PORT $GERRIT_1_SSHD_PORT $GERRIT_2_HOSTNAME $GERRIT_2_HTTPD_PORT $GERRIT_2_SSHD_PORT
echo "Remove replication plugin from gerrit site 1"
rm $LOCATION_TEST_SITE_1/plugins/replication.jar
echo "Starting gerrit site 1"
$LOCATION_TEST_SITE_1/bin/gerrit.sh restart
echo "Remove replication plugin from gerrit site 2"
rm $LOCATION_TEST_SITE_2/plugins/replication.jar
echo "Starting gerrit site 2"
$LOCATION_TEST_SITE_2/bin/gerrit.sh restart


if [[ $(ps -ax | grep haproxy | grep "gerrit_setup/ha-proxy-config" | awk '{print $1}' | wc -l) -lt 1 ]];then
  echo "Starting haproxy"
  start_ha_proxy
fi

echo "==============================="
echo "Current gerrit multi-site setup"
echo "==============================="
echo "The admin password is 'secret'"
echo "deployment-location=$DEPLOYMENT_LOCATION"
echo "replication-delay=$REPLICATION_DELAY_SEC"
echo "enable-https=$HTTPS_ENABLED"
echo
echo "GERRIT HA-PROXY: $GERRIT_CANONICAL_WEB_URL"
echo "GERRIT-1: http://$GERRIT_1_HOSTNAME:$GERRIT_1_HTTPD_PORT"
echo "GERRIT-2: http://$GERRIT_2_HOSTNAME:$GERRIT_2_HTTPD_PORT"
echo "Prometheus: http://localhost:9090"
echo
echo "Site-1: $LOCATION_TEST_SITE_1"
echo "Site-2: $LOCATION_TEST_SITE_2"
echo
echo "$HTTPS_CLONE_MSG"
echo

exit $?
