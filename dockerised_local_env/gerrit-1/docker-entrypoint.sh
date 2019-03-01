#!/bin/bash -ex

java -Xmx100g -jar /var/gerrit/bin/gerrit.war init -d /var/gerrit --batch --dev --install-all-plugins
java -Xmx100g -jar /var/gerrit/bin/gerrit.war reindex -d /var/gerrit --index accounts
java -Xmx100g -jar /var/gerrit/bin/gerrit.war reindex -d /var/gerrit --index groups

echo "Gerrit configuration:"
cat /var/gerrit/etc/gerrit.config
echo "Replication plugin configuration:"
cat /var/gerrit/etc/replication.config

echo "Starting git daemon"
/usr/local/bin/git-daemon.sh &

sed -i -e 's/\-\-console-log//g' /var/gerrit/bin/gerrit.sh
/var/gerrit/bin/gerrit.sh run
