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

## Execution

One starts environment by calling

```bash
./test.sh
```

Upon start docker logs are tailed to file in deployment dir. They will be printed to
the console (together with `docker-compose.yaml` used to start the setup) upon tests
failure (`test.sh` will exit with `1` in such case). No ports/volumes are exposed and
multiple test instance can be started in the same CI node (internal docker network is
used to perform tests).

# Manual tests development

One starts environment for tests development with

```bash
./test.sh --manual true
```

In this mode services are exposed under `localhost` with the following ports:
* `gerrit1`, *HTTP:8081*, *SSH:29418*, *debug:5005*
* `gerrit2`, *HTTP:8082*, *SSH:29419*, *debug:5006*
* `zookeeper`, *2181*
* `kafka`, *9092*

Note that `gerrit_tester` is also started (with `scenarios.sh` mounted)
and one can connect to it with:

```bash
docker exec -it gerrit_tester /bin/bash
```

to call `scenarios.sh` manualy.

One stops the setup by pressing `Ctrl+C` in the logs console. Note that
it will automatically perform the volumes cleanup but configuration is
still preserved under `/tmp/[random]` dir and one has a chance to keep it
by answering `2) No` to the following command prompt (displayed when the
tests setup gets successfully stopped):

```bash
Do you wish to delete deployment dir [/tmp/random_dir_name]?
1) Yes
2) No
#?
```

# Assumptions

It is assumed that both `multi-site` jar is available under in-tree gerrit repository
that is related to the `multi-site` repo (e.g.
`../../../bazel-bin/plugins/[multi-site/multi-site|replication/replication].jar`).
It can be further customised with `--multisite-lib-file` and `--replication-lib-file`
execution options accordingly. Full list of options can be obtained with:

```bash
./test.sh --help
```
