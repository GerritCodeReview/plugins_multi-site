# Dockerised test environment

The docker compose provided in this directory is meant to orchestrate the spin up
of a dockerised test environment. Run it with:

```bash
make init_all
```

The spin up will take a while, check what is going on with:

```bash
docker-compose logs -f
```
