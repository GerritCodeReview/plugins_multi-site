
@PLUGIN@ Configuration
=========================

The @PLUGIN@ plugin must be installed as a library module in the
`$GERRIT_SITE/lib` folder of all the instances and the following fields should
be specified in the `$site_path/etc/@PLUGIN@.config` file:

File '@PLUGIN@.config'
--------------------

## Sample configuration.

```
[broker "publisher"]
  enabled = true
  indexEventEnabled = true
  cacheEventEnabled = true
  projectListEventEnabled = true
  streamEventEnabled = true

[broker "subscriber"]
  enabled = true
  indexEventEnabled = true
  cacheEventEnabled = true
  projectListEventEnabled = true
  streamEventEnabled = true

[kafka]
  bootstrapServers = kafka-1:9092,kafka-2:9092,kafka-3:9092

  indexEventTopic = gerrit_index
  streamEventTopic = gerrit_stream
  cacheEventTopic = gerrit_cache_eviction
  projectListEventTopic = gerrit_project_list

[kafka "publisher"]
  compressionType = none
  deliveryTimeoutMs = 60000

[kafka "subscriber"]
  pollingIntervalMs = 1000

  enableAutoCommit = true
  autoCommitIntervalMs = 1000
  autoCommitIntervalMs = 5000

[ref-database "zookeeper"]
  connectString = "localhost:2181"
  rootNode = "/gerrit/multi-site"
  sessionTimeoutMs = 1000
  connectionTimeoutMs = 1000
  retryPolicyBaseSleepTimeMs = 1000
  retryPolicyMaxSleepTimeMs = 3000
  retryPolicyMaxRetries = 3
  casRetryPolicyBaseSleepTimeMs = 100
  casRetryPolicyMaxSleepTimeMs = 100
  casRetryPolicyMaxRetries = 3
  transactionLockTimeoutMs = 1000
```

## Configuration parameters

```broker.publisher.enabled```
:   Enable publishing events to broker
    Defaults: false

```broker.publisher.indexEventEnabled```
:   Enable publication of index events, ignored when `broker.publisher.enabled`
    is false

    Defaults: true

```broker.publisher.cacheEventEnabled```
:   Enable publication of cache events, ignored when `broker.publisher.enabled`
    is false

    Defaults: true

```broker.publisher.projectListEventEnabled```
:   Enable publication of project list events, ignored when `broker.publisher.enabled`
    is false

    Defaults: true

```broker.publisher.streamEventEnabled```
:   Enable publication of stream events, ignored when `broker.publisher.enabled`
    is false

    Defaults: true

```broker.subscriber.enabled```
:   Enable consuming of events from broker
    Defaults: false

```broker.subscriber.indexEventEnabled```
:   Enable consumption of index events, ignored when `broker.subscriber.enabled`
    is false

    Defaults: true

```broker.subscriber.cacheEventEnabled```
:   Enable consumption of cache events, ignored when `broker.subscriber.enabled`
    is false

    Defaults: true

```broker.subscriber.projectListEventEnabled```
:   Enable consumption of project list events, ignored when `broker.subscriber.enabled`
    is false

    Defaults: true

```broker.subscriber.streamEventEnabled```
:   Enable consumption of stream events, ignored when `broker.subscriber.enabled`
    is false

    Defaults: true

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

```kafka.bootstrapServers```
:	  List of Kafka broker hosts (host:port) to use for publishing events to the message
    broker

```kafka.indexEventTopic```
:   Name of the Kafka topic to use for publishing indexing events
    Defaults to GERRIT.EVENT.INDEX

```kafka.streamEventTopic```
:   Name of the Kafka topic to use for publishing stream events
    Defaults to GERRIT.EVENT.STREAM

```kafka.cacheEventTopic```
:   Name of the Kafka topic to use for publishing cache eviction events
    Defaults to GERRIT.EVENT.CACHE

```kafka.projectListEventTopic```
:   Name of the Kafka topic to use for publishing cache eviction events
    Defaults to GERRIT.EVENT.PROJECT.LIST

```kafka.subscriber.pollingIntervalMs```
:   Polling interval in milliseconds for checking incoming events

    Defaults: 1000

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

```ref-database.zookeeper.connectString```
:   Connection string to Zookeeper

```ref-database.zookeeper.rootNode```
:   Root node to use in Zookeeper to store/retrieve information

    Defaults: "/gerrit/multi-site"


```ref-database.zookeeper.sessionTimeoutMs```
:   Zookeeper session timeout in milliseconds

    Defaults: 1000

```ref-database.zookeeper.connectionTimeoutMs```
:   Zookeeper connection timeout in milliseconds

    Defaults: 1000

```ref-database.zookeeper.retryPolicyBaseSleepTimeMs```
:   Configuration for the base sleep timeout in milliseconds of the
    BoundedExponentialBackoffRetry policy used for the Zookeeper connection

    Defaults: 1000

```ref-database.zookeeper.retryPolicyMaxSleepTimeMs```
:   Configuration for the maximum sleep timeout in milliseconds of the
    BoundedExponentialBackoffRetry policy used for the Zookeeper connection

    Defaults: 3000

```ref-database.zookeeper.retryPolicyMaxRetries```
:   Configuration for the maximum number of retries of the
    BoundedExponentialBackoffRetry policy used for the Zookeeper connection

    Defaults: 3

```ref-database.zookeeper.casRetryPolicyBaseSleepTimeMs```
:   Configuration for the base sleep timeout in milliseconds of the
    BoundedExponentialBackoffRetry policy used for the Compare and Swap
    operations on Zookeeper

    Defaults: 1000

```ref-database.zookeeper.casRetryPolicyMaxSleepTimeMs```
:   Configuration for the maximum sleep timeout in milliseconds of the
    BoundedExponentialBackoffRetry policy used for the Compare and Swap
    operations on Zookeeper

    Defaults: 3000

```ref-database.zookeeper.casRetryPolicyMaxRetries```
:   Configuration for the maximum number of retries of the
    BoundedExponentialBackoffRetry policy used for the Compare and Swap
    operations on Zookeeper

    Defaults: 3

```ref-database.zookeeper.transactionLockTimeoutMs```
:   Configuration for the Zookeeper Lock timeout (in milliseconds) used when reading data
    from Zookeeper, applying the git local changes and writing the new objectId
    into Zookeeper

    Defaults: 1000

#### Custom kafka properties:

In addition to the above settings, custom Kafka properties can be explicitly set
for `publisher` and `subscriber`.

**NOTE**: custom Kafka properties will be ignored when the relevant subsection is
disabled (i.e. `broker.subscriber.enabled` and/or `broker.publisher.enabled` are
set to `false`).

The complete list of available settings can be found directly in the kafka website:

* **Publisher**: https://kafka.apache.org/documentation/#producerconfigs
* **Subscriber**: https://kafka.apache.org/documentation/#consumerconfigs
