#!/bin/bash -ex

echo "Starting git daemon"
/usr/local/bin/git-daemon.sh &

if [[ $INIT == 1 ]]; then
  echo "Initializing Gerrit..."
  java -jar /var/gerrit/bin/gerrit.war init -d /var/gerrit --batch --dev --install-all-plugins --no-auto-start
  java -jar /var/gerrit/bin/gerrit.war reindex -d /var/gerrit --index accounts
  java -jar /var/gerrit/bin/gerrit.war reindex -d /var/gerrit --index groups
fi

java -jar /var/gerrit/bin/gerrit.war daemon
