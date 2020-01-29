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

function check_application_requirements {
  type haproxy >/dev/null 2>&1 || { echo >&2 "Require haproxy but it's not installed. Aborting."; exit 1; }
  type java >/dev/null 2>&1 || { echo >&2 "Require java but it's not installed. Aborting."; exit 1; }
  type docker >/dev/null 2>&1 || { echo >&2 "Require docker but it's not installed. Aborting."; exit 1; }
  type docker-compose >/dev/null 2>&1 || { echo >&2 "Require docker-compose but it's not installed. Aborting."; exit 1; }
  type wget >/dev/null 2>&1 || { echo >&2 "Require wget but it's not installed. Aborting."; exit 1; }
  type envsubst >/dev/null 2>&1 || { echo >&2 "Require envsubst but it's not installed. Aborting."; exit 1; }
  type openssl >/dev/null 2>&1 || { echo >&2 "Require openssl but it's not installed. Aborting."; exit 1; }
}

function get_replication_url {
  REPLICATION_LOCATION_TEST_SITE=$1
  REPLICATION_HOSTNAME=$2
  USER=$REPLICATION_SSH_USER

  if [ "$REPLICATION_TYPE" = "file" ];then
    echo "url = file://$REPLICATION_LOCATION_TEST_SITE/git/#{name}#.git"
  elif [ "$REPLICATION_TYPE" = "ssh" ];then
    echo "url = ssh://$USER@$REPLICATION_HOSTNAME:$REPLICATION_LOCATION_TEST_SITE/git/#{name}#.git"
  fi
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
    export REPLICATION_LOCATION_TEST_SITE=$6
    export GERRIT_HOSTNAME=$7
    export REPLICATION_HOSTNAME=$8
    export REMOTE_DEBUG_PORT=$9
    export KAFKA_GROUP_ID=${10}
    export REPLICATION_URL=$(get_replication_url $REPLICATION_LOCATION_TEST_SITE $REPLICATION_HOSTNAME)

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

function deploy_config_files {
  # KAFKA configuration
  export KAFKA_PORT=9092

  # ZK configuration
  export ZK_PORT=2181

  # SITE 1
  GERRIT_SITE1_HOSTNAME=$1
  GERRIT_SITE1_HTTPD_PORT=$2
  GERRIT_SITE1_SSHD_PORT=$3
  CONFIG_TEST_SITE_1=$LOCATION_TEST_SITE_1/etc
  GERRIT_SITE1_REMOTE_DEBUG_PORT="5005"
  GERRIT_SITE1_KAFKA_GROUP_ID="instance-1"
  # SITE 2
  GERRIT_SITE2_HOSTNAME=$4
  GERRIT_SITE2_HTTPD_PORT=$5
  GERRIT_SITE2_SSHD_PORT=$6
  CONFIG_TEST_SITE_2=$LOCATION_TEST_SITE_2/etc
  GERRIT_SITE2_REMOTE_DEBUG_PORT="5006"
  GERRIT_SITE2_KAFKA_GROUP_ID="instance-2"

  # Set config SITE1
  copy_config_files $CONFIG_TEST_SITE_1 $GERRIT_SITE1_HTTPD_PORT $LOCATION_TEST_SITE_1 $GERRIT_SITE1_SSHD_PORT $GERRIT_SITE2_HTTPD_PORT $LOCATION_TEST_SITE_2 $GERRIT_SITE1_HOSTNAME $GERRIT_SITE2_HOSTNAME $GERRIT_SITE1_REMOTE_DEBUG_PORT $GERRIT_SITE1_KAFKA_GROUP_ID


  # Set config SITE2
  copy_config_files $CONFIG_TEST_SITE_2 $GERRIT_SITE2_HTTPD_PORT $LOCATION_TEST_SITE_2 $GERRIT_SITE2_SSHD_PORT $GERRIT_SITE1_HTTPD_PORT $LOCATION_TEST_SITE_1 $GERRIT_SITE1_HOSTNAME $GERRIT_SITE2_HOSTNAME $GERRIT_SITE2_REMOTE_DEBUG_PORT $GERRIT_SITE2_KAFKA_GROUP_ID
}


function cleanup_environment {
  echo "Killing existing HA-PROXY setup"
  kill $(ps -ax | grep haproxy | grep "gerrit_setup/ha-proxy-config" | awk '{print $1}') 2> /dev/null
  echo "Stoping kafka and zk"
  docker-compose -f $SCRIPT_DIR/docker-compose.kafka-broker.yaml down 2> /dev/null

  echo "Stoping GERRIT instances"
  $1/bin/gerrit.sh stop 2> /dev/null
  $2/bin/gerrit.sh stop 2> /dev/null

  echo "REMOVING setup directory $3"
  rm -rf $3 2> /dev/null
}

function check_if_kafka_is_running {
  echo $(docker inspect kafka_test_node 2> /dev/null | grep '"Running": true' | wc -l)
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
    echo "[--replication-type]            Options [file,ssh]; default ssh"
    echo "[--replication-ssh-user]        SSH user for the replication plugin; default $(whoami)"
    echo "[--replication-delay]           Replication delay across the two instances in seconds"
    echo
    echo "[--just-cleanup-env]            Cleans up previous deployment; default false"
    echo
    echo "[--enabled-https]               Enabled https; default true"
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
  "--replication-ssh-user" )
    export REPLICATION_SSH_USER=$2
    shift
    shift
  ;;
  "--replication-type")
    export REPLICATION_TYPE=$2
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
REPLICATION_TYPE=${REPLICATION_TYPE:-"ssh"}
REPLICATION_SSH_USER=${REPLICATION_SSH_USER:-$(whoami)}
export REPLICATION_DELAY_SEC=${REPLICATION_DELAY_SEC:-"5"}
export SSH_ADVERTISED_PORT=${SSH_ADVERTISED_PORT:-"29418"}
HTTPS_ENABLED=${HTTPS_ENABLED:-"false"}

COMMON_LOCATION=$DEPLOYMENT_LOCATION/gerrit_setup
LOCATION_TEST_SITE_1=$COMMON_LOCATION/instance-1
LOCATION_TEST_SITE_2=$COMMON_LOCATION/instance-2
HA_PROXY_CONFIG_DIR=$COMMON_LOCATION/ha-proxy-config
HA_PROXY_CERTIFICATES_DIR="$HA_PROXY_CONFIG_DIR/certificates"

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
  echo "Downloading websession-broker plugin stable 3.0"
  wget https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.0/job/plugin-websession-broker-bazel-stable-3.0/lastSuccessfulBuild/artifact/bazel-bin/plugins/websession-broker/websession-broker.jar \
  -O $DEPLOYMENT_LOCATION/websession-broker.jar || { echo >&2 "Cannot download websession-broker plugin: Check internet connection. Abort\
ing"; exit 1; }
  wget https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.0/job/plugin-healthcheck-bazel-stable-3.0/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar \
  -O $DEPLOYMENT_LOCATION/healthcheck.jar || { echo >&2 "Cannot download healthcheck plugin: Check internet connection. Abort\
ing"; exit 1; }
else
  echo "Without the websession-broker; user login via haproxy will fail."
fi

echo "Downloading zookeeper plugin stable 3.0"
  wget https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.0/job/plugin-zookeeper-gh-bazel-stable-3.0/lastSuccessfulBuild/artifact/bazel-bin/plugins/zookeeper/zookeeper.jar \
  -O $DEPLOYMENT_LOCATION/zookeeper.jar || { echo >&2 "Cannot download zookeeper plugin: Check internet connection. Abort\
ing"; exit 1; }

echo "Downloading events-broker library stable 3.0"
  wget https://repo1.maven.org/maven2/com/gerritforge/events-broker/3.0.5/events-broker-3.0.5.jar \
  -O $DEPLOYMENT_LOCATION/events-broker.jar || { echo >&2 "Cannot download events-broker library: Check internet connection. Abort\
ing"; exit 1; }

echo "Downloading kafka-events plugin stable 3.0"
  wget https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.0/job/plugin-kafka-events-bazel-stable-3.0/lastSuccessfulBuild/artifact/bazel-bin/plugins/kafka-events/kafka-events.jar \
  -O $DEPLOYMENT_LOCATION/kafka-events.jar || { echo >&2 "Cannot download kafka-events plugin: Check internet connection. Abort\
ing"; exit 1; }

if [ "$REPLICATION_TYPE" = "ssh" ];then
  echo "Using 'SSH' replication type"
  echo "Make sure ~/.ssh/authorized_keys and ~/.ssh/known_hosts are configured correctly"
fi

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
  cp -f $DEPLOYMENT_LOCATION/zookeeper.jar $LOCATION_TEST_SITE_1/plugins/zookeeper.jar

  echo "Copy events broker library"
  cp -f $DEPLOYMENT_LOCATION/events-broker.jar $LOCATION_TEST_SITE_1/lib/events-broker.jar

  echo "Copy kafka events plugin"
  cp -f $DEPLOYMENT_LOCATION/kafka-events.jar $LOCATION_TEST_SITE_1/plugins/kafka-events.jar

  echo "Re-indexing"
  java -jar $DEPLOYMENT_LOCATION/gerrit.war reindex -d $LOCATION_TEST_SITE_1
  # Replicating environment
  echo "Replicating environment"
  cp -fR $LOCATION_TEST_SITE_1/* $LOCATION_TEST_SITE_2

  echo "Link replication plugin"
  ln -s $LOCATION_TEST_SITE_1/plugins/replication.jar $LOCATION_TEST_SITE_1/lib/replication.jar
  ln -s $LOCATION_TEST_SITE_2/plugins/replication.jar $LOCATION_TEST_SITE_2/lib/replication.jar

  echo "Link multi-site library to plugin directory"
  ln -s $LOCATION_TEST_SITE_1/lib/multi-site.jar $LOCATION_TEST_SITE_1/plugins/multi-site.jar
  ln -s $LOCATION_TEST_SITE_2/lib/multi-site.jar $LOCATION_TEST_SITE_2/plugins/multi-site.jar
fi


IS_KAFKA_RUNNING=$(check_if_kafka_is_running)
if [ $IS_KAFKA_RUNNING -lt 1 ];then

  echo "Starting zk and kafka"
  docker-compose -f $SCRIPT_DIR/docker-compose.kafka-broker.yaml up -d
  echo "Waiting for kafka to start..."
  while [[ $(check_if_kafka_is_running) -lt 1 ]];do sleep 10s; done
fi

echo "Re-deploying configuration files"
deploy_config_files $GERRIT_1_HOSTNAME $GERRIT_1_HTTPD_PORT $GERRIT_1_SSHD_PORT $GERRIT_2_HOSTNAME $GERRIT_2_HTTPD_PORT $GERRIT_2_SSHD_PORT
echo "Starting gerrit site 1"
$LOCATION_TEST_SITE_1/bin/gerrit.sh restart
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
echo "replication-type=$REPLICATION_TYPE"
echo "replication-ssh-user=$REPLICATION_SSH_USER"
echo "replication-delay=$REPLICATION_DELAY_SEC"
echo "enable-https=$HTTPS_ENABLED"
echo
echo "GERRIT HA-PROXY: $GERRIT_CANONICAL_WEB_URL"
echo "GERRIT-1: http://$GERRIT_1_HOSTNAME:$GERRIT_1_HTTPD_PORT"
echo "GERRIT-2: http://$GERRIT_2_HOSTNAME:$GERRIT_2_HTTPD_PORT"
echo
echo "Site-1: $LOCATION_TEST_SITE_1"
echo "Site-2: $LOCATION_TEST_SITE_2"
echo
echo "$HTTPS_CLONE_MSG"
echo

exit $?
