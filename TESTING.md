# Testing Guide

This is the self-contained test runbook for the repository. Run every command from the repository
root. The default build contains unit tests only; the opt-in `e2e` profile adds live HTTP tests through
Playwright against local MySQL, Redis, and RabbitMQ.

## Prerequisites

- JDK 25 (`java -version`)
- Docker with Compose (`docker compose version`)
- Ports `3306`, `5672`, `6379`, and `15672` available
- A root `.env` copied from `.env.example`; `.env` is gitignored and must never be committed

Only secrets belong in `.env`. The MySQL and RabbitMQ passwords must match the local Compose services,
and `JWT_SIGNING_SECRET` must contain at least 32 bytes. The Playwright suite validates the OAuth redirect
but does not contact Google; the Google secret only needs to be present so the application can start.

```powershell
# Windows PowerShell
Copy-Item .env.example .env   # skip when .env already exists
```

```bash
# macOS / Linux
cp .env.example .env         # skip when .env already exists
```

## Unit tests and normal verification

The normal build needs no running infrastructure and excludes `*E2ETest.java`.

```powershell
# Windows
.\mvnw.cmd clean verify
```

```bash
# macOS / Linux
./mvnw clean verify
```

Expected result: **16 tests**, zero failures and errors.

## Playwright HTTP E2E tests

These tests use Playwright Java's `APIRequestContext`; no browser installation is required. They boot the
real Spring application on a random port and use the live local infrastructure.

### 1. Start and verify infrastructure

```powershell
docker compose --env-file .env -f infra/docker-compose.yml up -d
docker compose --env-file .env -f infra/docker-compose.yml ps
```

Wait until `demo-mysql`, `demo-redis`, and `demo-rabbitmq` report `healthy`.

### 2. Load `.env` into the current shell

Maven does not load `.env` automatically.

```powershell
# Windows PowerShell
Get-Content .env | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object {
  $name, $value = $_.Split('=', 2)
  Set-Item -Path "Env:$name" -Value $value
}
```

```bash
# macOS / Linux
set -a
. ./.env
set +a
```

### 3. Run the complete suite

```powershell
# Windows
.\mvnw.cmd -pl web -am verify -Pe2e
```

```bash
# macOS / Linux
./mvnw -pl web -am verify -Pe2e
```

Expected result: **16 unit tests + 8 E2E scenarios = 24 tests**, zero failures and errors.

The E2E suite covers:

- health, unauthenticated access, tenant isolation, unknown tenants, and OAuth state redirect
- experiment create, list, read, update, delete, validation, and ID preservation
- assignment identity generation/linking, deterministic repeat assignment, limits, invalid input, and degradation
- tracking validation, missing/unknown identity no-op behavior, idempotency, exposure, conversion, and orphan conversion
- delayed RabbitMQ ingestion into the event table and aggregate results/rates
- deterministic routing through all four RabbitMQ partition queues
- RabbitMQ-unavailable `503` behavior and recovery after broker restart
- results authorization/not-found behavior and final cleanup

The broker-outage scenario deliberately stops and restarts the local `demo-rabbitmq` container. Do not run
the E2E profile against shared infrastructure or in parallel with another local test run. The test restarts
RabbitMQ in a `finally` block and waits separately for Docker health and Spring Cloud Stream reconnection.

## Reports

- Unit reports: `<module>/target/surefire-reports/`
- E2E report: `web/target/failsafe-reports/`
- E2E class: `web/src/test/java/ai/visitorflow/demo/e2e/ExperimentApiE2ETest.java`

## Troubleshooting

- `JWT signing secret must contain at least 32 bytes`: load `.env` in the same shell that runs Maven and
  ensure `JWT_SIGNING_SECRET` is at least 32 bytes.
- MySQL access denied or RabbitMQ authentication failure: make the `.env` passwords match
  `infra/docker-compose.yml`, then recreate the affected local container if its credentials changed.
- Infrastructure is not healthy: inspect it with `docker compose --env-file .env -f
  infra/docker-compose.yml ps` and `docker compose --env-file .env -f infra/docker-compose.yml logs
  <service>`.
- RabbitMQ remains stopped after an interrupted run: execute
  `docker compose --env-file .env -f infra/docker-compose.yml start rabbitmq` and wait for it to become
  healthy.
- A failed asynchronous-results assertion: first confirm RabbitMQ is healthy, then inspect the application
  output and `web/target/failsafe-reports/`; the suite already allows for eventual ingestion and reconnects.
