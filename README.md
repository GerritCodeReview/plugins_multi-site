# Gerrit multi-site plugin

This plugin allows having multiple Gerrit masters to be deployed across various
sites without having to share any storage. The alignment between the masters
happens using the replication plugin and an external message broker.

The Gerrit masters requirements are:

- Gerrit v2.16.5 or later
- Migrated to NoteDb
- Connected to the same message broker
- Accessible behind a load balancer (e.g., HAProxy)

Currently, the only mode supported is one primary read/write master
and multiple read-only masters but eventually the plan is to support N
read/write masters. The read/write master is handling any traffic while the
read-only masters are serving the Gerrit GUI assets, the HTTP GET REST API and
the git-upload-packs. The read-only masters are kept updated to be always ready
to become a read/write master.

For more details on the overall multi-site design and roadmap, please refer
to the [multi-site plugin DESIGN.md document](DESIGN.md)

## License

This plugin is released under the same Apache 2.0 license and copyright holders
as of the Gerrit Code Review project.

## How to build

The multi-site plugin is built like any other Gerrit plugin in tree mode, by cloning
Gerrit and the multi-site plugin code, and checking them out on the desired branch.

Example of cloning Gerrit and multi-site for a stable-2.16 build:

```
git clone -b stable-2.16 https://gerrit.googlesource.com/gerrit
git clone -b stable-2.16 https://gerrit.googlesource.com/plugins/multi-site

cd gerrit/plugins
ln -s ../../multi-site .
rm external_plugin_deps.bzl
ln -s multi-site/external_plugin_deps.bzl .
```

Example of building the multi-site plugin:

```
cd gerrit
bazel build plugins/multi-site
```

The multi-site.jar plugin is generated to `bazel-genfiles/plugins/multi-site/multi-site.jar`.

Example of testing the multi-site plugin:

```
cd gerrit
bazel test plugins/multi-site:multi_site_tests
```

**NOTE**: The multi-site tests include also the use of Docker containers for
instantiating and using a Kafka/Zookeeper broker. Make sure you have a Docker
daemon running (/var/run/docker.sock accessible) or a DOCKER_HOST pointing to
a Docker server.

## How to configure

Install the multi-site plugin into the `$GERRIT_SITE/plugins` directory of all
the Gerrit servers that are part of the multi-site cluster.

Create the `$GERRIT_SITE/etc/multi-site.config` on all Gerrit servers with the
following basic settings:

```
[kafka]
  bootstrapServers = <kafka-host>:<kafka-port>

[kafka "publisher"]
  enabled = true

[kafka "subscriber"]
  enabled = true
```

For more details on the configuration settings, please refer to the
[multi-site configuration documentation](src/main/resources/Documentation/config.md).

You need also to setup the Git-level replication between nodes, for more details
please refer to the
[replication plugin documentation](https://gerrit.googlesource.com/plugins/replication/+/refs/heads/master/src/main/resources/Documentation/config.md).
