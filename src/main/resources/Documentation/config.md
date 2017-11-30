@PLUGIN@ Configuration
=========================

The @PLUGIN@ plugin must be installed on both instances and the following fields
should be specified in `$site_path/etc/@PLUGIN@.config` file:

File '@PLUGIN@.config'
--------------------

[main]
:  sharedDirectory = /directory/accessible/from/both/instances
[peerInfo]
:  strategy = static
[peerInfo "static"]
:  url = target_instance_url
[http]
:  user = username
:  password = password

[main]
:  sharedDirectory = /directory/accessible/from/both/instances
[peerInfo]
:  strategy = jgroups
[peerInfo "jgroups"]
:  myUrl = local_instance_url
[jgroups]
:  clusterName = foo
:  skipInterface = lo*
:  skipInterface = eth2
[http]
:  user = username
:  password = password
[healthcheck]
:  enable = true

main.sharedDirectory
:   Path to a directory accessible from both master instances.
    When given as a relative path, then it is resolved against the $SITE_PATH
    or Gerrit server. For example, if $SITE_PATH is "/gerrit/root" and
    sharedDirectory is given as "shared/dir" then the real path of the shared
    directory is "/gerrit/root/shared/dir". When not specified, the default
    is "shared".

peerInfo.strategy
:   Strategy to find other peers. Supported strategies are `static` or `jgroups`.
    Defaults to `static`.
* The `static` strategy allows to staticly configure the peer gerrit instance using
the configuration parameter `peerInfo.static.url`.
* The `jgroups` strategy allows that a gerrit instance discovers the peer
instance by using JGroups to send multicast messages. In this case the
configuration parameters `peerInfo.jgroups.*` are used to control the sending of
the multicast messages. During startup each instance will advertise its address
over a JGroups multicast message. JGroups takes care to inform each cluster when
a member joins or leaves the cluster.

peerInfo.static.url
:   Specify the URL for the peer instance.

peerInfo.jgroups.myUrl
:   The URL of this instance to be broadcast to other peers. If not specified, the
    URL is determined from the `httpd.listenUrl` in the `gerrit.config`.
    If `httpd.listenUrl` is configured with multiple values, is configured to work
    with a reverse proxy (i.e. uses `proxy-http` or `proxy-https` scheme), or is
    configured to listen on all local addresses (i.e. using hostname `*`), then
    the URL must be explicitly specified with `myUrl`.

jgroups.clusterName
:   The name of the high-availability cluster. When peers discover themselves dynamically this
    name is used to determine which instances should work together.  Only those Gerrit
    interfaces which are configured for the same clusterName will communicate with each other.
    Defaults to "GerritHA".

jgroups.skipInterface
:   A name or a wildcard of network interface(s) which should be skipped
    for JGroups communication. Peer discovery may fail if the host has multiple
    network interfaces and an inappropriate interface is chosen by JGroups.
    This option can be repeated many times in the `jgroups` section.
    Defaults to the list of: `lo*`, `utun*`, `awdl*` which are known to be
    inappropriate for JGroups communication.

NOTE: To work properly in certain environments, JGroups needs the System property
`java.net.preferIPv4Stack` to be set to `true`.
See (http://jgroups.org/tutorial/index.html#_trouble_shooting).

http.user
:   Username to connect to the peer instance.

http.password
:   Password to connect to the peer instance.

@PLUGIN@ plugin uses REST API calls to keep the target instance in-sync. It
is possible to customize the parameters of the underlying http client doing these
calls by specifying the following fields:

http.connectionTimeout
:   Maximum interval of time in milliseconds the plugin waits for a connection
    to the target instance. When not specified, the default value is set to 5000ms.

http.socketTimeout
:   Maximum interval of time in milliseconds the plugin waits for a response from the
    target instance once the connection has been established. When not specified,
    the default value is set to 5000ms.

http.maxTries
:   Maximum number of times the plugin should attempt when calling a REST API in
    the target instance. Setting this value to 0 will disable retries. When not
    specified, the default value is 5. After this number of failed tries, an
    error is logged.

http.retryInterval
:   The interval of time in milliseconds between the subsequent auto-retries.
    When not specified, the default value is set to 1000ms.

cache.synchronize
:   Whether to synchronize cache evictions.
    Defaults to true.

cache.threadPoolSize
:   Maximum number of threads used to send cache evictions to the target instance.
    Defaults to 1.

cache.pattern
:   Pattern to match names of custom caches for which evictions should be
    forwarded (in addition to the core caches that are always forwarded). May be
    specified more than once to add multiple patterns.
    Defaults to an empty list, meaning only evictions of the core caches are
    forwarded.

event.synchronize
:   Whether to synchronize stream events.
    Defaults to true.

index.synchronize
:   Whether to synchronize secondary indexes.
    Defaults to true.

index.threadPoolSize
:   Maximum number of threads used to send index events to the target instance.
    Defaults to 1.

websession.synchronize
:   Whether to synchronize web sessions.
    Defaults to true.

websession.cleanupInterval
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

healthcheck.enable
:   Whether to enable the health check endpoint. Defaults to 'true'.
