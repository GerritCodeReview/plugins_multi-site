# Gerrit multi-site plugin

This plugin allows to deploy a distributed cluster of multiple Gerrit masters
each using a separate site without sharing any storage. The alignment between
the masters happens using the replication plugin and an external message broker.

Requirements for the Gerrit masters are:

- Gerrit v2.16.5 or later
- Migrated to NoteDb
- Connected to the same message broker
- Accessible via a load balancer (e.g. HAProxy)

**NOTE**: The multi-site plugin will not start if Gerrit is not yet migrated
to NoteDb.

Supports multiple read/write masters across multiple sites across different
geographic locations. The Gerrit nodes are kept synchronized
between each other using the replication plugin and a global ref-database in
order to detect and prevent split-brains.

For more details on the overall multi-site design and roadmap, please refer
to the [multi-site plugin DESIGN.md document](DESIGN.md)

## License

This plugin is released under the same Apache 2.0 license and copyright holders
as of the Gerrit Code Review project.

## How to build

The multi-site plugin can only be built in tree mode, by cloning
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

The multi-site.jar plugin is generated to `bazel-bin/plugins/multi-site/multi-site.jar`.

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

Install the multi-site plugin into the `$GERRIT_SITE/lib` directory of all
the Gerrit servers that are part of the multi-site cluster.
Create a symbolic link from `$GERRIT_SITE/lib/multi-site.jar` into the
`$GERRIT_SITE/plugins`.

Add the multi-site module to `$GERRIT_SITE/etc/gerrit.config` as follows:

```
[gerrit]
  installDbModule = com.googlesource.gerrit.plugins.multisite.GitModule
  installModule = com.googlesource.gerrit.plugins.multisite.Module
```

For more details on the configuration settings, please refer to the
[multi-site configuration documentation](src/main/resources/Documentation/config.md).

You also need to setup the Git-level replication between nodes, for more details
please refer to the
[replication plugin documentation](https://gerrit.googlesource.com/plugins/replication/+/refs/heads/master/src/main/resources/Documentation/config.md).
