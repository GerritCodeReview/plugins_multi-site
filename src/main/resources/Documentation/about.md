The @PLUGIN@ plugin allows to synchronize the eviction of caches between two
Gerrit instances sharing the same repositories and database.

The plugin needs to be installed in both instances and every time a cache
eviction occurs in one of the instances, the other instance's cache is updated.
This way, both caches are kept synchronized.

For this synchronization to work, http must be enabled in both instances and the
plugin must be configured with valid credentials. For further information, refer
to [config](config.html) documentation.
