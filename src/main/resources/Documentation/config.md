
@PLUGIN@ Configuration
=========================

The @PLUGIN@ plugin must be installed as a library module in the
`$GERRIT_SITE/lib` folder of all the instances and linked to
`$GERRIT_SITE/plugins`, which enable to use it as both libModule
and plugin.
Configuration should be specified in the `$site_path/etc/@PLUGIN@.config` file.

## Configuration parameters

```cache.synchronize```
:   Whether to synchronize cache evictions. Set to false when relying on
    low cache TTLs and therefore cache eviction is not strictly needed.
    Defaults to true.

```cache.threadPoolSize```
:   Maximum number of threads used to send cache evictions to the target
    instance.

    Defaults to 4.

```cache.pattern```
:   Pattern to match names of custom caches for which evictions should be
    forwarded (in addition to the core caches that are always forwarded). May be
    specified more than once to add multiple patterns.

    Defaults to an empty list, meaning only evictions of the core caches are
    forwarded.

```event.synchronize```
:   Whether to synchronize stream events. Set to false when not using the SSH
    stream events.
    Defaults to true.

```index.numStripedLocks```
:   Number of striped locks to use during reindexing of secondary indexes.
    Defaults to 10

```index.synchronize```
:   Whether to synchronize secondary indexes. Set to false when using multi-site
    on Gerrit replicas that do not have an index, or when using an external
    service such as ElasticSearch.
    Defaults to true.

```index.threadPoolSize```
:   Maximum number of threads used to send index events to the target instance.
    Defaults to 4.

```index.maxTries```
:   Maximum number of times the plugin should attempt to reindex changes.
    Setting this value to 0 will disable retries. After this number of failed
    tries, an error is logged and the local index should be considered stale and
    needs to be investigated and manually reindexed.

    Defaults to 2.

```index.retryInterval```
:   The time interval in milliseconds between subsequent auto-retries.
    Defaults to 30000 (30 seconds).

```broker.indexEventTopic```
:   Name of the topic to use for publishing indexing events
    Defaults to GERRIT.EVENT.INDEX

`broker.streamEventTopic`
:   Name of the topic to use for publishing all stream events.
    Default: gerrit

```broker.cacheEventTopic```
:   Name of the topic to use for publishing cache eviction events
    Defaults to GERRIT.EVENT.CACHE

```broker.projectListEventTopic```
:   Name of the topic to use for publishing cache eviction events
    Defaults to GERRIT.EVENT.PROJECT.LIST

```broker.streamEventPublishTimeoutMs```
:   The timeout in milliseconds for publishing stream events.
    Defaults to 30000 (30 seconds).

**NOTE**: All broker settings are ignored when all of the `cache`,
`index` or `event` synchronization is disabled.

```ref-database.enabled```
:   Enable the use of a shared ref-database
    Defaults: true

```ref-database.replicationLagRefreshInterval```
:   Enable the auto-refresh of the metrics to trace the auto-replication
    lag by polling on a regular basis. Set to zero for disabling the polling
    mechanism.
    Defaults: 60 min

```ref-database.enforcementRules.<policy>```
:   Level of consistency enforcement across sites on a project:refs basis.
    Supports two values for enforcing the policy on multiple projects or refs.
    If the project or ref is omitted, apply the policy to all projects or all refs.

    The <policy> can be one of the following values:

    1. REQUIRED - Throw an exception if a git ref-update is processed again
    a local ref not yet in sync with the shared ref-database.
    The user transaction is cancelled. The Gerrit GUI (or the Git client)
    receives an HTTP 500 - Internal Server Error.

    2. IGNORED - Ignore any validation against the shared ref-database.

    *Example:*
    ```
    [ref-database "enforcementRules"]
       IGNORED = AProject:/refs/heads/feature
    ```

    Ignore the alignment with the shared ref-database for AProject on refs/heads/feature.

    Defaults: No rules = All projects are REQUIRED to be consistent on all refs.

```projects.pattern```
:   Specifies which projects events should be send via broker. It can be provided more
    than once, and supports three formats: regular expressions, wildcard matching, and single
    project matching. All three formats match case-sensitive.

    Values starting with a caret `^` are treated as regular
    expressions. For the regular expressions details please follow
    official [java documentation](https://docs.oracle.com/javase/tutorial/essential/regex/).

    Please note that regular expressions could also be used
    with inverse match.

    Values that are not regular expressions and end in `*` are
    treated as wildcard matches. Wildcards match projects whose
    name agrees from the beginning until the trailing `*`. So
    `foo/b*` would match the projects `foo/b`, `foo/bar`, and
    `foo/baz`, but neither `foobar`, nor `bar/foo/baz`.

    Values that are neither regular expressions nor wildcards are
    treated as single project matches. So `foo/bar` matches only
    the project `foo/bar`, but no other project.

    By default, all projects are matched.

```replication.push-filter.minWaitBeforeReloadLocalVersionMs```
:   Specifies the minimum amount of time in milliseconds replication plugin filter will
    wait before retrying check for ref which is not up to date with global-refdb.

    By default: 1000 milliseconds

```replication.push-filter.maxRandomWaitBeforeReloadLocalVersionMs```
:   Specifies the additional amount of time in milliseconds replication filter will
    wait before retrying check for ref which is not up to date with global-refdb.

    If maxRandomWaitBeforeReloadLocalVersionMs is set to zero random sleep for not in sync
    refs is disabled.

    By default: 1000 milliseconds

```replication.fetch-filter.minWaitBeforeReloadLocalVersionMs```
:   Specifies the minimum amount of time in milliseconds pull-replication filter wait
    before retrying check for ref which is not up to date with global-refdb.

    By default: 1000 milliseconds

```replication.fetch-filter.maxRandomWaitBeforeReloadLocalVersionMs```
:   Specifies the additional amount of time in milliseconds pull-replication filter will
    wait before retrying check for ref which is not up to date with global-refdb.

    If maxRandomWaitBeforeReloadLocalVersionMs is set to zero random sleep for not in sync
    refs is disabled.

    By default: 1000 milliseconds

## Replication filters

The @PLUGIN@ plugin is also responsible for filtering out replication events that may
risk to create a split-brain situation.
It integrates the push and pull replication filtering extension points for validating
the refs to be replicated and dropping some of them.

**Replication plugin**

When using the Gerrit core replication plugin, also known as push-replication, link the
`replication.jar` to the `$GERRIT_SITE/lib` directory and add the following libModule
to `gerrit.config`:

```
[gerrit]
  installModule = com.googlesource.gerrit.plugins.replication.ReplicationExtensionPointModule
```

The above configuration would be automatically detected by the @PLUGIN@ plugin which would then
install the PushReplicationFilterModule for filtering outgoing replication refs based
on their global-refdb status:

- Outgoing replication of refs that are NOT up-to-date with the global-refdb will be
  discarded, because they may cause split-brain on the remote replication endpoints.

- All other refs will be pushed as normal to the remote replication ends.

**Pull-replication plugin**

When using the [pull-replication](https://gerrit.googlesource.com/plugins/pull-replication)
plugin, link the `pull-replication.jar` to the `$GERRIT_SITE/lib` directory and add the following
two libModules to `gerrit.config`:

```
[gerrit]
        installModule = com.googlesource.gerrit.plugins.replication.pull.ReplicationExtensionPointModule
```

The above configuration would be automatically detected by the @PLUGIN@ plugin which would then
install the PullReplicationFilterModule for filtering incoming fetch replication refs based
on their global-refdb status.

- Incoming replication of refs that locally are already up-to-date with the global-refdb will be
  discarded, because they would not add anything more to the current status of the local refs.

- All other refs will be fetched as normal from the replication sources.
