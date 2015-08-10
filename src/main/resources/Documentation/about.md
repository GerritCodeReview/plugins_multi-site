The @PLUGIN@ plugin allows to synchronize secondary indexes between two Gerrit
instances sharing the same git repositories and database.

The plugin is installed in both instances and every time the secondary index
is modified in one of the instances, i.e., a change is added, updated or removed
from the index, the other instance index is updated accordingly. This way, both
indexes are kept synchronized.

For this secondary index synchronization to work, http must be enabled in both
instances and the plugin must be configured with valid credentials. For further
information, refer to [config](config.html) documentation.
