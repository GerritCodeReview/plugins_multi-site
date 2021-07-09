This plugin allows having multiple Gerrit masters to be deployed across
various sites without having to share any storage. The alignment between
the masters happens using the replication plugin and an external message
broker.

This plugin allows Gerrit to publish and to consume events over a
message broker for aligning with the other masters over different sites.

The masters must be:
* Gerrit instance id is mandatory for @PLUGIN@ plugin. All the master 
  must have [gerrit.instanceId](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#gerrit) populated.
* events-broker library must be installed as a library module in the
  `$GERRIT_SITE/lib` directory of all the masters
* global-refdb library must be installed as a library module in the
  `$GERRIT_SITE/lib` directory of all the masters
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

* Message broker deployed in a multi-master setup across all the sites

For the masters:

* Install and configure @PLUGIN@ plugin

For further information and supported options, refer to [config](config.md)
documentation.

## Metrics

@PLUGIN@ plugin exposes following metrics:

### Broker message publisher
* Broker message published count

`metric=plugins/multi-site/multi_site/broker/broker_message_publisher_counter/broker_msg_publisher_counter, type=com.codahale.metrics.Meter`

* Broker failed to publish message count

`metric=plugins/multi-site/multi_site/broker/broker_message_publisher_failure_counter/broker_msg_publisher_failure_counter, type=com.codahale.metrics.Meter`

### Message subscriber
* Subscriber message consumed count

`metric=plugins/multi-site/multi_site/subscriber/subscriber_message_consumer_counter/subscriber_msg_consumer_counter, type=com.codahale.metrics.Meter`

* Subscriber failed to consume message count

`metric=plugins/multi-site/multi_site/subscriber/subscriber_message_consumer_failure_counter/subscriber_msg_consumer_failure_counter, type=com.codahale.metrics.Meter`

* Subscriber failed to poll messages count

`metric=plugins/multi-site/multi_site/subscriber/subscriber_message_consumer_failure_counter/subscriber_msg_consumer_poll_failure_counter, type=com.codahale.metrics.Meter`

* Subscriber replication lag (sec behind the producer)

`metric=site/multi_site/subscriber/subscriber_replication_status/sec_behind, type=com.google.gerrit.metrics.dropwizard.CallbackMetricImpl`
