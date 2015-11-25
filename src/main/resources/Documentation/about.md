The @PLUGIN@ plugin allows to share stream events between two Gerrit instances
sharing the same git repositories and database.

The plugin needs to be installed in both instances and every time a stream event occurs in
one of the instances (see [more events info]
(https://gerrit-review.googlesource.com/Documentation/cmd-stream-events.html#events)),
the event is forwarded to the other instance which re-plays it. This way, the output
of the stream-events command is the same, no matter what instance a client is
connected to.

For this to work, http must be enabled in both instances and the plugin
must be configured with valid credentials. For further information, refer to
[config](config.html) documentation.
