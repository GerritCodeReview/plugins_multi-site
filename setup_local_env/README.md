# Local environment setup

This script configures a full environment to simulate a Gerrit Multi-Site setup.
The environment is composed by:

- 2 gerrit instances deployed by default in /tmp
- 1 kafka node and 1 zookeeper node
- 1 HA-PROXY

## Requirements

- java
- docker and docker-compose
- wget
- envsubst
- haproxy

## Examples

Simplest setup with all default values and cleanup previous deployment

```bash
sh setup_local_env/setup.sh --release-war-file /path/to/release.war --multisite-plugin-file /path/to/multi-site.jar
```

Cleanup the previous deployments

```bash
sh setup_local_env/setup.sh --just-cleanup-env true
```

Help

```bash
Usage: sh setup.sh [--option ]

[--release-war-file]            Location to release.war file
[--multisite-plugin-file]       Location to plugin multi-site.jar file

[--new-deployment]              Cleans up previous gerrit deployment and re-installs it. default true
[--get-websession-plugin]       Download websession-flatfile plugin from CI lastSuccessfulBuild; default true
[--deployment-location]         Base location for the test deployment; default /tmp

[--gerrit-canonical-host]       The default host for Gerrit to be accessed through; default localhost
[--gerrit-canonical-port]       The default port for Gerrit to be accessed throug; default 8080

[--gerrit-ssh-advertised-port]  Gerrit Instance 1 sshd port; default 29418

[--gerrit1-httpd-port]          Gerrit Instance 1 http port; default 18080
[--gerrit1-sshd-port]           Gerrit Instance 1 sshd port; default 39418

[--gerrit2-httpd-port]          Gerrit Instance 2 http port; default 18081
[--gerrit2-sshd-port]           Gerrit Instance 2 sshd port; default 49418

[--replication-type]            Options [file,ssh]; default ssh
[--replication-ssh-user]        SSH user for the replication plugin; default $(whoami)
[--just-cleanup-env]            Cleans up previous deployment; default false

[--enabled-https]               Enabled https; default true
```

## Limitations

- Assumes the ssh replication is done always on port 22 on both instances
- When cloning projects via ssh, public keys entries are added to `known_hosts`
  - Clean up the old entries when doing a new deploymet, otherwise just use HTTP
