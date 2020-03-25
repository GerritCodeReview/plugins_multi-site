# Dockerised test environment

## Prerequisites

* envsubst:

```bash
brew install gettext
brew link --force gettext
```

* wget:

```bash
brew install wget
```

## Instructions

The docker compose provided in this directory is meant to orchestrate the spin up
of a dockerised test environment with the latest stable Gerrit version.
Run it with:

```bash
make init_all
```

The spin up will take a while, check what is going on with:

```bash
docker-compose logs -f
```

*NOTE:* If you want to run any ssh command as admin you can use the ssh keys into the *gerrit-{1,2}/ssh* directory.

If you need to restart one of the Gerrit instances to simulate, for example,
an upgrade, you can do it this way:

```bash
make restart_gerrit_1 # (or make restart_gerrit_2)
```

## How to test

Consider the
[instructions](https://gerrit-review.googlesource.com/Documentation/dev-e2e-tests.html)
on how to use Gerrit core's Gatling framework, to run non-core test scenarios
such as this plugin one below:

```bash
sbt "gatling:testOnly com.googlesource.gerrit.plugins.multisite.scenarios.CloneUsingMultiGerrit1"
```

This is a scenario that can serve as an example for how to start testing a
multi-site Gerrit system, here such as this dockerized one. That scenario tries
to clone a project created on this dockerized multi Gerrit, from gerrit-1 (port
8081). The scenario therefore expects Gerrit multi-site to have properly
synchronized the new project from the up node gerrit-2 to gerrit-1. That
project gets deleted after by the (so aggregate) scenario.

Scenario scala source files and their companion json resource ones are stored
under the usual src/test directories. That structure follows the scala package
one from the scenario classes. The core framework expects such a directory
structure for both the scala and resources (json data) files.
