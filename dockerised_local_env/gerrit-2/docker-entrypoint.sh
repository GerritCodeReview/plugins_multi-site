#!/bin/bash -ex

echo "Starting git daemon"
/usr/local/bin/git-daemon.sh &

if [[ $INIT == 1 ]]; then
  java -jar /var/gerrit/bin/gerrit.war init -d /var/gerrit --batch --dev --no-auto-start --install-all-plugins

  echo "Remove git repos created during init phase"
  rm -fr /var/gerrit/git/*

  echo "Waiting for gerrit1 server to become available."
  sleep 120

  chmod go-r /var/gerrit/.ssh/id_rsa
  ssh-keyscan -t rsa -p 29418 gerrit-1 > /var/gerrit/.ssh/known_hosts
  ssh -p 29418 admin@gerrit-1 replication start

  echo "Waiting for replication to complete."
  sleep 30
fi

java -jar /var/gerrit/bin/gerrit.war daemon
