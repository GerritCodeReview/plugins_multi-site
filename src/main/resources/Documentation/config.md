@PLUGIN@ Configuration
=========================

The @PLUGIN@ plugin must be installed in both instances and the following fields
should be specified in the corresponding Gerrit configuration file:

File 'gerrit.config'
--------------------

[plugin "@PLUGIN@"]
:  url = target_instance_url
:  user = username
:  password = password

plugin.@PLUGIN@.url
:   Specify the URL for the secondary (target) instance.

plugin.@PLUGIN@.user
:   Username to connect to the secondary (target) instance.

plugin.@PLUGIN@.password
:   Password to connect to the secondary (target) instance. This value can
     also be defined in secure.config.

@PLUGIN@ plugin uses REST API calls to keep the target instance in-sync. It
is possible to customize the parameters of the underlying http client doing these
calls by specifying the following fields:

@PLUGIN@.connectionTimeout
:   Maximum interval of time in milliseconds the plugin waits for a connection
    to the target instance. When not specified, the default value is set to 5000ms.

@PLUGIN@.socketTimeout
:   Maximum interval of time in milliseconds the plugin waits for a response from the
    target instance once the connection has been established. When not specified,
    the default value is set to 5000ms.

@PLUGIN@.maxTries
:   Maximum number of times the plugin should attempt when calling a REST API in
    the target instance. Setting this value to 0 will disable retries. When not
    specified, the default value is 5. After this number of failed tries, an
    error is logged.

@PLUGIN@.retryInterval
:   The interval of time in milliseconds between the subsequent auto-retries.
    When not specified, the default value is set to 1000ms.

@PLUGIN@.indexThreadPoolSize
:   Maximum number of threads used to send index events to the target instance.
    Defaults to 1.

@PLUGIN@.eventThreadPoolSize
:   Maximum number of threads used to send stream events to the target instance.
    Defaults to 1.
