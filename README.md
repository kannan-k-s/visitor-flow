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

- **Data plane** (hot, public, browser-facing) — `GET /{tenant}/v1/assign`,
  `POST /{tenant}/v1/track`. Latency-critical, **anonymous (no auth)**, rate-limited,
  **fail-safe: never breaks the page** (degrades to the **default variant**).
- **Control plane** (warm, private, human-facing) — experiment CRUD + results
  dashboard. OAuth + RBAC.

Assignment is, by default, a **pure deterministic hash** of visitor identity — no
I/O on the render path, sticky across restarts with zero storage. See `design.md`
§6 for the assignment engine, strategies, and fallback chain.

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 25 |
| Framework | Spring Boot 4.0.7 |
| Source of truth | MySQL |
| Cache / counters | Redis |
| Async / streaming | Kafka |
| Schema migrations | Liquibase |
| Auth | Google OAuth2 + RBAC (control plane); data plane anonymous |
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
mvnw.cmd clean verify                    # build the whole reactor (compile + test + package)
mvnw.cmd -pl web -am spring-boot:run     # run the app (web module bootstraps the rest)

# macOS / Linux
./mvnw clean verify
./mvnw -pl web -am spring-boot:run
```

Once running:
- API docs (Swagger UI): `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

## Configuration

Copy `.env.example` to `.env` and fill in real values. **Never commit `.env`** —
it is gitignored; only `.env.example` (placeholders, no secrets) is tracked.

## Project structure

Multi-module Maven build; the split follows the plane boundary (`design.md` §2):

```
demo (parent)
├── data/       shared persistence + domain — entities, tenant-scoped repos, request
│               context, MySQL/Redis/Kafka config       (both planes depend on it)
├── visitor/    data plane — /{tenant}/v1/assign, /{tenant}/v1/track, strategies
│               (hash | swrr), identity, rate limiting          (depends on data)
├── admin/      control plane — experiment config, results, RBAC (depends on data)
└── web/        runnable app — DemoApplication, OAuth + JWT, filter wiring
                (depends on admin + visitor; the only executable jar)
```

Per-module root packages are `…demo.data`, `…demo.visitor`, `…demo.admin`, and
`…demo` (web). Modules are fleshed out as the core is implemented — see
[`AGENTS.md`](./AGENTS.md) §4.

## Engineering conventions

All code follows the rules in [`AGENTS.md`](./AGENTS.md) — versioned APIs, thin
controllers, constructor injection, tenant-scoped repositories, ModelMapper-based
mapping, and a 2-space / K&R code style. `CLAUDE.md` wires the same rules into AI
tooling used on this project.

---

Company-neutral by design: no third-party product or company name appears anywhere
in the code, commits, or documentation.
