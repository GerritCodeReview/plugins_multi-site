#!/bin/bash -ex

java -Xmx100g -jar /var/gerrit/bin/gerrit.war init -d /var/gerrit --batch --dev --no-auto-start --install-all-plugins
#
echo "Gerrit configuration:"
cat /var/gerrit/etc/gerrit.config
echo "Replication plugin configuration:"
cat /var/gerrit/etc/replication.config

echo "Remove git repos created during init phase"
rm -fr /var/gerrit/git/*

echo "Starting git daemon"
/usr/local/bin/git-daemon.sh &

echo "Waiting for initial replication"
sleep 120

java -Xmx100g -jar /var/gerrit/bin/gerrit.war daemon
