# infra — local development stack

Self-contained Docker Compose stack for the service's dependencies. Everything
lives under this folder.

> **Always pass `--env-file .env` and run from the repo root.** The compose file
> guards required secrets with `${VAR:?...}`, so *every* command (including `down`)
> fails fast if the env file is not loaded. `.env` lives at the **repo root**, not in
> `infra/`, and Compose does not auto-discover it from there.

```bash
# from the repo root
docker compose --env-file .env -f infra/docker-compose.yml up -d
docker compose --env-file .env -f infra/docker-compose.yml ps
docker compose --env-file .env -f infra/docker-compose.yml logs -f
docker compose --env-file .env -f infra/docker-compose.yml down       # keeps named volumes
docker compose --env-file .env -f infra/docker-compose.yml down -v    # drops named volumes
```

Prefer not to repeat the flag? Export the vars once, then use plain commands:

```bash
set -a; source .env; set +a
docker compose -f infra/docker-compose.yml up -d
```

## Services and endpoints

| Service | Host address | Credentials | Notes |
|---|---|---|---|
| nginx | http://localhost | — | Serves the admin SPA **and** reverse-proxies the app (port 80) |
| mysql | localhost:3306 | `root` / `root` | Data in the `mysql-data` volume |
| rabbitmq | localhost:5672 | `root` / `root` | AMQP broker; data in `rabbitmq-data` |
| rabbitmq management | http://localhost:15672 | `root` / `root` | Broker topology and metrics |
| redis | localhost:6379 | none | Snapshots in `./redis/data` |

The Spring app itself runs **on the host** at `host.docker.internal:8080` (not in
Compose): `java -jar web/target/web-0.0.1-SNAPSHOT.jar` with the same `.env` loaded.

## nginx

`nginx/nginx.conf` and `nginx/conf.d/*.conf` are mounted read-only, and the static
admin SPA is mounted from `../ui` (repo-root `ui/`) at `/usr/share/nginx/html`. nginx
is the single public origin on port 80 and does two jobs:

- **Serves the SPA** at extensionless, tenant-prefixed routes —
  `http://localhost/{tenant}/login`, `/{tenant}/experiments`, `/{tenant}/analytics`
  (each maps to one static HTML file; the page reads the tenant from the URL).
- **Reverse-proxies** everything backend-owned to `host.docker.internal:8080`:
  the versioned APIs `/{tenant}/v1/...` (including `/v1/auth/login`, the OAuth start),
  the OAuth callback `/login/...`, `/oauth2/...`, `/swagger-ui`, `/v3/api-docs`, and
  `/actuator/...`. `GET /nginx-health` returns `ok` without touching the backend.
- **Redirects the bare/tenant-less entry points** to the default local tenant so
  plain `http://localhost` just works: `/` and `/login` → `/demo/login`,
  `/experiments` → `/demo/experiments`, `/analytics` → `/demo/analytics`. From the
  login page an already-signed-in user is forwarded on to `/demo/experiments`
  (cookie-aware). The default tenant is hard-coded as `demo` in `conf.d/default.conf`
  — change it if your local tenant differs.

> The OAuth callback path `/login/oauth2/code/google` is tenant-less by design — the
> tenant travels in the signed OAuth `state`. Register that exact redirect URI
> (`http://localhost/login/oauth2/code/google`) in the Google OAuth client.

Editing config vs. the SPA:

```bash
# config-only change (nginx.conf / conf.d) — a reload is enough
docker compose --env-file .env -f infra/docker-compose.yml exec nginx nginx -s reload

# changed the mounted set of files or volumes — recreate the container
docker compose --env-file .env -f infra/docker-compose.yml up -d nginx
```

Editing the SPA files under `../ui` needs no restart — they are served live from the
mount.

## Persistence

MySQL and RabbitMQ use the `mysql-data` and `rabbitmq-data` named volumes, so
their data survives `docker compose down`. Redis persists `dump.rdb` under
`infra/redis/data`. Use `docker compose down -v` to reset named-volume data.

## RabbitMQ partition topology

Spring Cloud Stream provisions one direct exchange named `tracking-events` and
four queues for consumer group `tracking-events-v0`. The producer hashes
`anonVisitorId` to partition `0..3`; each routing key is bound to exactly one
queue. This preserves per-anonymous-visitor ordering while distributing work
across four independently consumable queues:

```text
tracking-events exchange
  routing key tracking-events-0 -> tracking-events.tracking-events-v0-0
  routing key tracking-events-1 -> tracking-events.tracking-events-v0-1
  routing key tracking-events-2 -> tracking-events.tracking-events-v0-2
  routing key tracking-events-3 -> tracking-events.tracking-events-v0-3
```

The application connects with:

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=root
spring.rabbitmq.password=${RABBITMQ_PASSWORD}
```
