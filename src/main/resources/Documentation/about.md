
This plugin allows making Gerrit highly available by having redundant Gerrit
masters.

The masters must be:

* connecting to the same database
* sharing the git repositories using a shared file system (e.g. NFS)
* behind a load balancer (e.g. HAProxy)

Currently, the mode supported is one primary (active) master and multiple backup
(passive) masters but eventually the plan is to support `n` active masters. In
the active/passive mode, the active master is handling all traffic while the
passives are kept updated to be always ready to take over.

Even if database and git repositories are shared by the masters, there are a few
areas of concern in order to be able to switch traffic between masters in a
transparent manner from the user's perspective. The 4 areas of concern are
things that Gerrit stores either in memory or locally in the review site:

* caches
* secondary indexes
* stream-events
* web sessions

They need either to be shared or kept local to each master but synchronized.
This plugin needs to be installed in all the masters and it will take care of sharing
or synchronizing them.

#### Caches
Every time a cache eviction occurs in one of the masters, the eviction will be
forwarded the other masters so their caches do not contain stale entries.

#### Secondary indexes
Every time the secondary index is modified in one of the masters, e.g., a change
is added, updated or removed from the index, the others master's index are
updated accordingly. This way, both indexes are kept synchronized.

#### Stream events
Every time a stream event occurs in one of the masters (see [more events info]
(https://gerrit-review.googlesource.com/Documentation/cmd-stream-events.html#events)),
the event is forwarded to the other masters which re-plays it. This way, the
output of the stream-events command is the same, no matter which master a client
is connected to.

#### Web session
The built-in Gerrit H2 based web session cache is replaced with a file based
implementation that is shared amongst the masters.

## Setup

Prerequisites:

* Unique database server must be accessible from all the masters
* Git repositories must be located on a shared file system
* A directory on a shared file system must be available for @PLUGIN@ to use

For the masters:

* Configure database section in gerrit.config to use the shared database
* Configure gerrit.basePath in gerrit.config to the shared repositories location
* Install and configure @PLUGIN@ plugin

Here is an example of the minimal @PLUGIN@.config:

Primary master

```
[main]
  sharedDirectory = /directory/accessible/from/both/masters

[peerInfo "static"]
  url = http://backupMasterHost1:8081/

[http]
  user = username
  password = password
```

Backup master

```
[main]
  sharedDirectory = /directory/accessible/from/both/masters

[peerInfo "static"]
  url = http://primaryMasterHost:8080/

[http]
  user = username
  password = password
```

For further information and supported options, refer to [config](config.md)
documentation.
