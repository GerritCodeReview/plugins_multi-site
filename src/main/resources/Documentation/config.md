
@PLUGIN@ Configuration
=========================

The @PLUGIN@ plugin must be installed as a library module in the
`$GERRIT_SITE/lib` folder of all the instances. Configuration should
be specified in the `$site_path/etc/@PLUGIN@.config` file.

## Configuration parameters

```cache.synchronize```
:   Whether to synchronize cache evictions.
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
:   Whether to synchronize stream events.
    Defaults to true.

```index.numStripedLocks```
:   Number of striped locks to use during reindexing of secondary indexes.
    Defaults to 10

```index.synchronize```
:   Whether to synchronize secondary indexes.
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

```broker.streamEventTopic```
:   Name of the topic to use for publishing stream events
    Defaults to GERRIT.EVENT.STREAM

```broker.cacheEventTopic```
:   Name of the topic to use for publishing cache eviction events
    Defaults to GERRIT.EVENT.CACHE

```broker.projectListEventTopic```
:   Name of the topic to use for publishing cache eviction events
    Defaults to GERRIT.EVENT.PROJECT.LIST

```ref-database.enabled```
:   Enable the use of a shared ref-database
    Defaults: true

```ref-database.enforcementRules.<policy>```
:   Level of consistency enforcement across sites on a project:refs basis.
    Supports multiple values for enforcing the policy on multiple projects or refs.
    If the project or ref is omitted, apply the policy to all projects or all refs.

    The <policy> can be one of the following values:

    1. REQUIRED - Throw an exception if a git ref-update is processed again
    a local ref not yet in sync with the shared ref-database.
    The user transaction is cancelled. The Gerrit GUI (or the Git client)
    receives an HTTP 500 - Internal Server Error.

    2. DESIRED - Validate the git ref-update against the shared ref-database.
    Any misaligned is logged in errors_log file but the user operation is allowed
    to continue successfully.

    3. IGNORED - Ignore any validation against the shared ref-database.

    *Example:*
    ```
    [ref-database "enforcementRules"]
       DESIRED = AProject:/refs/heads/feature
    ```

    Relax the alignment with the shared ref-database for AProject on refs/heads/feature.

    Defaults: No rules = All projects are REQUIRED to be consistent on all refs.

```projects.pattern```
:   Specifies which projects events should be send via broker. It can be provided more
    than once, and supports three formats: regular expressions, wildcard matching, and single
    project matching. All three formats match case-sensitive.

    Values starting with a caret `^` are treated as regular
    expressions. `^foo/(bar|baz)` would match the projects
    `foo/bar`, and `foo/baz`. Regular expressions have to fully
    match the project name. So the above example would not match
    `foo/bar2`, while `^foo/(bar|baz).*` would.

    Projects may be excluded from replication by using a regular
    expression with inverse match. `^(?:(?!PATTERN).)*$` will
    exclude any project that matches.

    Values that are not regular expressions and end in `*` are
    treated as wildcard matches. Wildcards match projects whose
    name agrees from the beginning until the trailing `*`. So
    `foo/b*` would match the projects `foo/b`, `foo/bar`, and
    `foo/baz`, but neither `foobar`, nor `bar/foo/baz`.

    Values that are neither regular expressions nor wildcards are
    treated as single project matches. So `foo/bar` matches only
    the project `foo/bar`, but no other project.

    By default, all events for all projects are send.
