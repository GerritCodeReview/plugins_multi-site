# Local environment setup

This script configures a full environment to simulate a Gerrit Multi-Site setup.
The environment is composed by:

- 2 gerrit instances deployed by default in /tmp
- 1 zookeeper node
- 1 Broker node (kafka, kinesis or gcloud-pubsub)
- 1 HA-PROXY

## Requirements

- java
- docker and docker-compose
- wget
- envsubst
- haproxy
- aws-cli (only when broker_type is "kinesis")

## Examples

Simplest setup with all default values and cleanup previous deployment. This
will deploy kafka broker

```bash
sh setup_local_env/setup.sh --release-war-file /path/to/gerrit.war --multisite-lib-file /path/to/multi-site.jar
```

Deploy Kinesis broker

```bash
sh setup_local_env/setup.sh \
    --release-war-file /path/to/gerrit.war \
    --multisite-lib-file /path/to/multi-site.jar \
    --broker-type kinesis
```

Deploy GCloud PubSub broker

```bash
sh setup_local_env/setup.sh \
    --release-war-file /path/to/gerrit.war \
    --multisite-lib-file /path/to/multi-site.jar \
    --broker-type gcloud-pubsub
```


Cleanup the previous deployments

```bash
sh setup_local_env/setup.sh --just-cleanup-env true
```

Help

```bash
Usage: sh ./setup.sh [--option ]

[--release-war-file]            Location to release.war file
[--multisite-lib-file]          Location to lib multi-site.jar file

[--new-deployment]              Cleans up previous gerrit deployment and re-installs it. default true
[--get-websession-plugin]       Download websession-broker plugin from CI lastSuccessfulBuild; default true
[--deployment-location]         Base location for the test deployment; default /tmp

[--gerrit-canonical-host]       The default host for Gerrit to be accessed through; default localhost
[--gerrit-canonical-port]       The default port for Gerrit to be accessed throug; default 8080

[--gerrit-ssh-advertised-port]  Gerrit Instance 1 sshd port; default 29418

[--gerrit1-httpd-port]          Gerrit Instance 1 http port; default 18080
[--gerrit1-sshd-port]           Gerrit Instance 1 sshd port; default 39418

[--gerrit2-httpd-port]          Gerrit Instance 2 http port; default 18081
[--gerrit2-sshd-port]           Gerrit Instance 2 sshd port; default 49418

[--replication-delay]           Replication delay across the two instances in seconds

[--just-cleanup-env]            Cleans up previous deployment; default false

[--enabled-https]               Enabled https; default true

[--broker_type]                 events broker type; 'kafka', 'kinesis' or 'gcloud-pubsub'. Default 'kafka'
```

## Limitations

- Assumes the ssh replication is done always on port 22 on both instances
- When cloning projects via ssh, public keys entries are added to `known_hosts`
  - Clean up the old entries when doing a new deployment, otherwise just use HTTP
