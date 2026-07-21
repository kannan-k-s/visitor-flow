# infra — local development stack

Self-contained Docker Compose stack for the service's dependencies. Everything
lives under this folder.

```bash
# from the repo root
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
docker compose -f infra/docker-compose.yml logs -f
docker compose -f infra/docker-compose.yml down       # keeps named volumes
docker compose -f infra/docker-compose.yml down -v    # drops named volumes
```

## Services and endpoints

| Service | Host address | Credentials | Notes |
|---|---|---|---|
| nginx | http://localhost | — | Config from `./nginx` (read-only) |
| mysql | localhost:3306 | `root` / `root` | Data in the `mysql-data` volume |
| rabbitmq | localhost:5672 | `root` / `root` | AMQP broker; data in `rabbitmq-data` |
| rabbitmq management | http://localhost:15672 | `root` / `root` | Broker topology and metrics |
| redis | localhost:6379 | none | Snapshots in `./redis/data` |

## nginx

`nginx/nginx.conf` and `nginx/conf.d/*.conf` are mounted read-only. Reload after
editing them:

```bash
docker compose -f infra/docker-compose.yml exec nginx nginx -s reload
```

`default.conf` reverse-proxies to the Spring Boot app on the host
(`host.docker.internal:8080`). `GET /nginx-health` returns `ok` without touching
the backend.

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
