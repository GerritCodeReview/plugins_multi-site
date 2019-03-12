#!/bin/bash -ex

echo "Starting git daemon"
/usr/local/bin/git-daemon.sh &

if [[ $INIT == 1 ]]; then
  java -Xmx100g -jar /var/gerrit/bin/gerrit.war init -d /var/gerrit --batch --dev --no-auto-start --install-all-plugins

  echo "Remove git repos created during init phase"
  rm -fr /var/gerrit/git/*

  echo "Waiting for initial replication"
  sleep 120
fi

java -Xmx100g -jar /var/gerrit/bin/gerrit.war daemon
