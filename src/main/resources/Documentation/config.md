
@PLUGIN@ Configuration
=========================

The @PLUGIN@ plugin must be installed on all the instances and the following fields
should be specified in `$site_path/etc/@PLUGIN@.config` file:

File '@PLUGIN@.config'
--------------------

## Sample configuration.

```
[kafka]
  bootstrapServers = kafka-1:9092,kafka-2:9092,kafka-3:9092

  indexEventTopic = gerrit_index
  streamEventTopic = gerrit_stream
  cacheEventTopic = gerrit_cache_eviction
  projectListEventTopic = gerrit_project_list

[kafka "publisher"]
  enabled = true

  indexEventEnabled = true
  cacheEventEnabled = true
  projectListEventEnabled = true
  streamEventEnabled = true

  KafkaProp-compressionType = none
  KafkaProp-deliveryTimeoutMs = 60000

[kafka "subscriber"]
  enabled = true
  pollingIntervalMs = 1000

  KafkaProp-enableAutoCommit = true
  KafkaProp-autoCommitIntervalMs = 1000
  KafkaProp-autoCommitIntervalMs = 5000

  indexEventEnabled = true
  cacheEventEnabled = true
  projectListEventEnabled = true
  streamEventEnabled = true

[split-brain]
  enabled = true
  
[split-brain "zookeeper"]
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
```

## Configuration parameters

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

```kafka.bootstrapServers```
:	List of Kafka broker hosts:port to use for publishing events to the message broker

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

```kafka.publisher.enabled```
:   Enable publishing events to Kafka
    Defaults: false

```kafka.publisher.indexEventEnabled```
:   Enable publication of index events, ignored when `kafka.publisher.enabled` is false
    Defaults: true

```kafka.publisher.cacheEventEnabled```
:   Enable publication of cache events, ignored when `kafka.publisher.enabled` is false
    Defaults: true

```kafka.publisher.projectListEventEnabled```
:   Enable publication of project list events, ignored when `kafka.publisher.enabled` is false
    Defaults: true

```kafka.publisher.streamEventEnabled```    
:   Enable publication of stream events, ignored when `kafka.publisher.enabled` is false
    Defaults: true

```kafka.subscriber.enabled```
:   Enable consuming of events from Kafka
    Defaults: false

```kafka.subscriber.indexEventEnabled```
:   Enable consumption of index events, ignored when `kafka.subscriber.enabled` is false
    Defaults: true

```kafka.subscriber.cacheEventEnabled```
:   Enable consumption of cache events, ignored when `kafka.subscriber.enabled` is false
    Defaults: true

```kafka.subscriber.projectListEventEnabled```
:   Enable consumption of project list events, ignored when `kafka.subscriber.enabled` is false
    Defaults: true

```kafka.subscriber.streamEventEnabled```    
:   Enable consumption of stream events, ignored when `kafka.subscriber.enabled` is false
    Defaults: true

```kafka.subscriber.pollingIntervalMs```
:   Polling interval for checking incoming events
    Defaults: 1000
    
```split-brain.zookeeper.connectString```
:   Connection string to  zookeeper
    
```split-brain.zookeeper.rootNode```
:   Root node to use under Zookeeper to store/retrieve information
    Defaults: "/gerrit/multi-site"
    
```split-brain.zookeeper.rootNode```
:   Root node to use under Zookeeper to store/retrieve information
    Defaults: "/gerrit/multi-site"
    
```split-brain.zookeeper.sessionTimeoutMs```
:   Root node to use under Zookeeper to store/retrieve information
    Defaults: "/gerrit/multi-site"
    
```split-brain.zookeeper.connectionTimeoutMs```
:   Root node to use under Zookeeper to store/retrieve information
    Defaults: "/gerrit/multi-site"
    
```split-brain.zookeeper.retryPolicyBaseSleepTimeMs```
:   Configuration for the base sleep timeout (iun ms) to use to create the BoundedExponentialBackoffRetry policy
used for the Zookeeper connection
    Defaults: 1000 ms
    
```split-brain.zookeeper.retryPolicyMaxSleepTimeMs```
:   Configuration for the max sleep timeout (iun ms) to use to create the BoundedExponentialBackoffRetry policy
used for the Zookeeper connection
    Defaults: 3000 ms
    
```split-brain.zookeeper.retryPolicyMaxRetries```
:   Configuration for the max number of retries to use to create the BoundedExponentialBackoffRetry policy
used for the Zookeeper connection
    Defaults: 3 
    
```split-brain.zookeeper.casRetryPolicyBaseSleepTimeMs```
:   Configuration for the base sleep timeout (iun ms) to use to create the BoundedExponentialBackoffRetry policy
used for the Compare and Swap operations on Zookeeper
    Defaults: 1000 ms
    
```split-brain.zookeeper.casRetryPolicyMaxSleepTimeMs```
:   Configuration for the max sleep timeout (iun ms) to use to create the BoundedExponentialBackoffRetry policy
used for the Compare and Swap operations on Zookeeper
    Defaults: 3000 ms
    
```split-brain.zookeeper.casRetryPolicyMaxRetries```
:   Configuration for the max number of retries to use to create the BoundedExponentialBackoffRetry policy
used for the Compare and Swap operations on Zookeeper
    Defaults: 3 
    
    
#### Custom kafka properties:

In addition to the above settings, custom Kafka properties can be explicitly set for `publisher` and `subscriber`.
In order to be acknowledged, these properties need to be prefixed with the `KafkaProp-` prefix and then camelCased,
as follows: `KafkaProp-yourPropertyValue`

For example, if you want to set the `auto.commit.interval.ms` property for your consumers, you will need to configure
this property as `KafkaProp-autoCommitIntervalMs`.

**NOTE**: custom kafka properties will be ignored when the relevant subsection is disabled (i.e. `kafka.subscriber.enabled`
and/or `kafka.publisher.enabled` are set to `false`).

The complete list of available settings can be found directly in the kafka website:

* **Publisher**: https://kafka.apache.org/documentation/#producerconfigs
* **Subscriber**: https://kafka.apache.org/documentation/#consumerconfigs