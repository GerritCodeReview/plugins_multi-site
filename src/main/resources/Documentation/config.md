
@PLUGIN@ Configuration
=========================

The @PLUGIN@ plugin must be installed on all the instances and the following fields
should be specified in `$site_path/etc/@PLUGIN@.config` file:

File '@PLUGIN@.config'
--------------------

### Static definition of the high-availability nodes.

```
[main]
  sharedDirectory = /directory/accessible/from/both/instances
[autoReindex]
  enabled = false
[peerInfo]
  strategy = static
[peerInfo "static"]
  url = first_target_instance_url
  url = second_target_instance_url
[http]
  user = username
  password = password
```

```main.sharedDirectory```
:   Path to a directory accessible from both master instances.
    When given as a relative path, then it is resolved against the $SITE_PATH
    or Gerrit server. For example, if $SITE_PATH is "/gerrit/root" and
    sharedDirectory is given as "shared/dir" then the real path of the shared
    directory is "/gerrit/root/shared/dir". When not specified, the default
    is "shared".

```autoReindex.enabled```
:   Enable the tracking of the latest change indexed under data/high-availability
    for each of the indexes. At startup scans all the changes, accounts and groups
    and reindex the ones that have been updated by other nodes while the server was down.
    When not specified, the default is "false", that means no automatic tracking
    and indexing at start.

```autoReindex.delay```
:   When autoReindex is enabled, indicates the delay aftere the plugin startup,
    before triggering the conditional reindexing of all changes, accounts and groups.
    Delay is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, the default is "10 seconds".

```autoReindex.pollInterval```
:   When autoReindex is enabled, indicates the interval between the conditional
    reindexing of all changes, accounts and groups.
    Delay is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, polling of conditional reindexing is disabled.

```autoReindex.interval```
:   Enable the tracking of the latest change indexed under data/high-availability
    for each of the indexes. At startup scans all the changes, accounts and groups
    and reindex the ones that have been updated by other nodes while the server was down.
    When not specified, the default is "false", that means no automatic tracking
    and indexing at start.

```peerInfo.strategy```
:   Strategy to find other peers. The only supported strategy is `static`.
    Defaults to `static`.
* The `static` strategy allows to staticly configure the peer gerrit instance using
the configuration parameter `peerInfo.static.url`.

```peerInfo.static.url```
:   Specify the URL for the peer instance. If more than one peer instance is to be
    configured, add as many url entries as necessary.

```http.user```
:   Username to connect to the peer instance.

```http.password```
:   Password to connect to the peer instance.

@PLUGIN@ plugin uses REST API calls to keep the target instance in-sync. It
is possible to customize the parameters of the underlying http client doing these
calls by specifying the following fields:

```http.connectionTimeout```
:   Maximum interval of time in milliseconds the plugin waits for a connection
    to the target instance. When not specified, the default value is set to 5000ms.

```http.socketTimeout```
:   Maximum interval of time in milliseconds the plugin waits for a response from the
    target instance once the connection has been established. When not specified,
    the default value is set to 5000ms.

```http.maxTries```
:   Maximum number of times the plugin should attempt when calling a REST API in
    the target instance. Setting this value to 0 will disable retries. When not
    specified, the default value is 360. After this number of failed tries, an
    error is logged.

```http.retryInterval```
:   The interval of time in milliseconds between the subsequent auto-retries.
    When not specified, the default value is set to 10000ms.

NOTE: the default settings for `http.timeout` and `http.maxTries` ensure that
the plugin will keep retrying to forward a message for one hour.

```cache.synchronize```
:   Whether to synchronize cache evictions.
    Defaults to true.

```cache.threadPoolSize```
:   Maximum number of threads used to send cache evictions to the target instance.
    Defaults to 4.

```cache.pattern```
:   Pattern to match names of custom caches for which evictions should be
    forwarded (in addition to the core caches that are always forwarded). May be
    specified more than once to add multiple patterns.
    Defaults to an empty list, meaning only evictions of the core caches are
    forwarded.

```event.synchronize```
:   Whether to synchronize stream events.
    Defaults to true.

```index.numStripedLocks```
:   Number of striped locks to use for during secondary indexes reindex.
    Defaults to 10

```index.synchronize```
:   Whether to synchronize secondary indexes.
    Defaults to true.

```index.threadPoolSize```
:   Maximum number of threads used to send index events to the target instance.
    Defaults to 4.

```index.maxTries```
:   Maximum number of times the plugin should attempt to reindex changes.
    Setting this value to 0 will disable retries. After this number of failed tries,
    an error is logged and the local index should be considered stale and needs
    to be investigated and manually reindexed.
    Defaults to 2.

```index.retryInterval```
:   The interval of time in milliseconds between the subsequent auto-retries.
    Defaults to 30000 (30 seconds).

```websession.synchronize```
:   Whether to synchronize web sessions.
    Defaults to true.

```websession.cleanupInterval```
:   Frequency for deleting expired web sessions. Values should use common time
    unit suffixes to express their setting:
* s, sec, second, seconds
* m, min, minute, minutes
* h, hr, hour, hours
* d, day, days
* w, week, weeks (`1 week` is treated as `7 days`)
* mon, month, months (`1 month` is treated as `30 days`)
* y, year, years (`1 year` is treated as `365 days`)
If a time unit suffix is not specified, `hours` is assumed.
Defaults to 24 hours.

```healthcheck.enable```
:   Whether to enable the health check endpoint. Defaults to 'true'.
