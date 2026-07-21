# Experimentation & Assignment Service

A multi-tenant service that answers one question on every page load —
**"which variant should this visitor see right now?"** — and later records
exposures and conversions so it can report which variant is winning.

It sits on the **critical path of page rendering**: every visit to every customer
site calls it. That single constraint shapes the whole design — latency, failure
behaviour, and the read/write split all derive from it.

> **The primary deliverable is the design document: [`design.md`](./design.md).**
> It records the architecture, the reasoning behind every significant decision, the
> trade-offs deliberately accepted, and what was left for later.

## Status

Design complete; core implementation in progress. This repository currently
contains the design document, the Spring Boot project skeleton, and the
engineering conventions the code is built against ([`AGENTS.md`](./AGENTS.md)).

## Architecture at a glance

Two planes with opposite requirements:

- **Data plane** (hot, public, browser-facing) — `GET /v1/assign`,
  `POST /v1/track/*`. Latency-critical, API-key auth, rate-limited, **fail-safe:
  never breaks the page** (degrades to the control variant).
- **Control plane** (warm, private, human-facing) — experiment CRUD + results
  dashboard. OAuth + RBAC.

Assignment is, by default, a **pure deterministic hash** of visitor identity — no
I/O on the render path, sticky across restarts with zero storage. See `design.md`
§7 for the assignment engine, strategies, and fallback chain.

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 25 |
| Framework | Spring Boot 4.0.7 |
| Source of truth | MySQL |
| Cache / counters | Redis |
| Async / streaming | Kafka |
| Schema migrations | Liquibase |
| Auth | Spring Security (OAuth2 + API keys) |
| Object mapping | ModelMapper |
| Boilerplate | Lombok |
| API docs | springdoc-openapi (Swagger UI) |

## Prerequisites

- **JDK 25**
- **MySQL**, **Redis**, and **Kafka** reachable (connection details via
  environment — see [`.env.example`](./.env.example)). Each is required only once
  its corresponding feature is wired.

## Build & run

The Maven wrapper is included — no local Maven install needed.

```bash
# Windows
mvnw.cmd clean verify          # compile + test + package
mvnw.cmd spring-boot:run       # run locally

# macOS / Linux
./mvnw clean verify
./mvnw spring-boot:run
```

Once running:
- API docs (Swagger UI): `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

## Configuration

Copy `.env.example` to `.env` and fill in real values. **Never commit `.env`** —
it is gitignored; only `.env.example` (placeholders, no secrets) is tracked.

## Project structure

```
src/main/java/ai/visitorflow/demo
  common/       request context, web filters, config, exceptions
  experiment/   control plane: experiment CRUD & lifecycle
  assignment/   data plane: /v1/assign, hash | swrr strategies, fallback
  tracking/     data plane: /v1/track/exposure|conversion
  results/      control plane: per-experiment aggregates
```

Modules are added as the core is implemented — see [`AGENTS.md`](./AGENTS.md) §4.

## Engineering conventions

All code follows the rules in [`AGENTS.md`](./AGENTS.md) — versioned APIs, thin
controllers, constructor injection, tenant-scoped repositories, ModelMapper-based
mapping, and a 2-space / K&R code style. `CLAUDE.md` wires the same rules into AI
tooling used on this project.

---

Company-neutral by design: no third-party product or company name appears anywhere
in the code, commits, or documentation.
