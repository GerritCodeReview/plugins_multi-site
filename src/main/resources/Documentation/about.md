This plugin allows having multiple Gerrit masters to be deployed across
various sites without having to share any storage. The alignment between
the masters happens using the replication plugin and an external message
broker.

This plugin allows Gerrit to publish and to consume events over a Kafka
message broker for aligning with the other masters over different sites.

The masters must be:

* migrated to NoteDb
* connected to the same message broker
* behind a load balancer (e.g., HAProxy)

Currently, the mode supported is one primary read/write master and multiple
read-only masters but eventually the plan is to support `n` read/write masters.
The read/write master is handling any traffic while the
read-only masters are serving the Gerrit GUI assets, the HTTP GET REST API and
git-upload-pack. The read-only masters are kept updated to be always
ready to become a read/write master.

The areas of alignment between the masters are:

1. caches
2. secondary indexes
3. stream-events
4. web sessions

This plugin is focussing on only the points 1. to 3., while other plugins can be
used to manage the replication of 4. across sites.

This plugin needs to be installed as a library module in the
`$GERRIT_SITE/lib`directory of all the masters, and it will take care of
keeping 1., 2. and 3. aligned across all the nodes.

#### Caches
Every time a cache eviction occurs in one of the masters, the eviction will be
published to the message broker so that other masters can consume the message
and evict the potential stale entries.

#### Secondary indexes
Every time the secondary index is modified in one of the masters, e.g., a change
is added, updated or removed from the index, an indexing event is published to the
message broker so that the other masters can consume the message and keep their indexes
updated accordingly. This way, all indexes across masters across sites are kept synchronized.

#### Stream events
Every time a stream event occurs in one of the masters (see [more events info]
(https://gerrit-review.googlesource.com/Documentation/cmd-stream-events.html#events)),
the event is published to the message broker so that other masters can consume it and
re-play it for all the connected SSH sessions that are listening to stream events.
This way, the output of the stream-events command is the same, no matter which masters a client
is connected to.


## Setup

Prerequisites:

* Kafka message broker deployed in a multi-master setup across all the sites

For the masters:

* Install and configure @PLUGIN@ plugin

Here is an example of minimal @PLUGIN@.config:

For all the masters on all the sites:

```
[kafka]
  bootstrapServers = kafka-1:9092,kafka-2:9092,kafka-3:9092
  eventTopic = gerrit_index

[kafka "publisher"]
  enable = true
  indexEventTopic = gerrit_index
  streamEventTopic = gerrit_stream
  cacheEvictionEventTopic = gerrit_cache_eviction

[kafka "subscriber"]
  enable = true
  pollingIntervalMs = 1000
  autoCommitIntervalMs = 1000
```


For further information and supported options, refer to [config](config.md)
documentation.

## Metrics

@PLUGIN@ plugin exposes following metrics:

### Validation
* Ref-update operations, split-brain detected and prevented

`metric=multi_site/validation/git_update_split_brain_prevented_total, type=com.codahale.metrics.Meter`
* Ref-update operation left node in a split-brain scenario

`metric=multi_site/validation/git_update_split_brain_total, type=com.codahale.metrics.Meter`

### Kafka broker message producer
* Broker message produced count

`multi_site/kafka/broker/kafka_broker_message_producer_counter/kafka_broker_msg_producer_counter, type=com.codahale.metrics.Meter`
* Broker failed to produce message count

`metric=multi_site/kafka/broker/kafka_broker_msg_producer_failure_counter, type=com.codahale.metrics.Meter`
