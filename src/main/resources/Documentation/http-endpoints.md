HTTP endpoints
=========================

The @PLUGIN@ plugin also provides HTTP endpoints, as described here below:

## replication-lag

Admin users can query for the replication lag in order to understand what
projects' replication is running behind and by how much (in milliseconds).

The results are returned in a map ordered in descending order by the replication
lag, so that the most behind projects are returned first.

Whilst some lag information is also available as a metric (see
the [documentation](./about.md#metrics)), this endpoint provides more
information since it shows _which_ project is associated to _which specific lag.

You can query the endpoint (at the receiving end of the replication) as follows:

```bash
curl -v -XGET -u <admin> '<gerrit>/a/plugins/multi-site/replication-lag?[limit=LIMIT]'
```

Output example:

```
)]}'
{
  "All-Users": 62,
  "bar": 13,
  "foo": 0,
  "some/other/project": 1451,
  "baz": 6432
}
```

Optionally the REST endpoint can receive the following additional arguments:

* limit=LIMIT

maximum number of projects to return
*default:10*