# Local e2e tests

This script configures an environment to simulate a Gerrit Multi-Site setup so
that automated e2e tests (so far only startup and check if all necessary plugins
were loaded) could be performed by CI.

The environment is composed by:
* 2 gerrit instances deployed by default in `/tmp/[random]`
* 1 zookeeper node
* 1 broker node (kafka - implemented, kinesis or gcloud-pubsub - to be implemented
  if/when needed)

## Requirements

- java
- docker and docker-compose
- wget
- envsubst
- git

## Execution

Tests defined in `scenarios.sh` are called automatically when plugin tests are called:

```bash
bazel test //plugins/multi-site/...
```

But one can also start them independently with:

```bash
./test.sh
```

Upon start docker logs are tailed to file in deployment dir. They will be printed to
the console (together with `docker-compose.yaml` used to start the setup) upon tests
failure (`test.sh` will exit with `1` in such case). No ports/volumes are exposed and
multiple test instances can be started in the same CI node (internal docker network is
used to perform tests).

# Assumptions

When called in the independent mode it is assumed that `multi-site` jar is available
under in-tree gerrit repository that is related to the `multi-site` repo (e.g.
`../../../bazel-bin/plugins/[multi-site/multi-site|replication/replication].jar`).
It can be further customised with `--multisite-lib-file` execution option. Full list
of options can be obtained with:

```bash
./test.sh --help
```
