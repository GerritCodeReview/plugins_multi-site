@PLUGIN@ Configuration
=========================

The @PLUGIN@ plugin must be installed in both instances and the following fields
should be specified in `$site_path/etc/@PLUGIN@.config` file:

File '@PLUGIN@.config'
--------------------

[main]
:  sharedDirectory = /directory/accessible/from/both/instances
[peerInfo]
:  url = target_instance_url
[http]
:  user = username
:  password = password

main.sharedDirectory
:   Path to a directory accessible from both master instances.
    When given as a relative path, then it is resolved against the $SITE_PATH
    or Gerrit server. For example, if $SITE_PATH is "/gerrit/root" and
    sharedDirectory is given as "shared/dir" then the real path of the shared
    directory is "/gerrit/root/shared/dir".

peerInfo.url
:   Specify the URL for the secondary (target) instance.

http.user
:   Username to connect to the secondary (target) instance.

http.password
:   Password to connect to the secondary (target) instance.

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
