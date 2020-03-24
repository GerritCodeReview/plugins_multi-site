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
