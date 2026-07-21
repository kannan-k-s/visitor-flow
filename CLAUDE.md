# CLAUDE.md

Entry point for Claude Code in this repository. The full engineering standard is
in **@AGENTS.md** — it is the single source of truth for conventions and is
imported here so it always loads. Read it. The 11 project rules are law.

## Quick reference — the 11 rules (details in @AGENTS.md §2)

1. **Versioned APIs** — `/v1/...`, controllers in `controller.v1`.
2. **Thin controllers** — controller calls exactly one service method and returns
   its DTO. No logic, no repositories, no entities in controllers. Services return
   **DTOs, never models/entities**.
3. **`@Builder @Getter`, never setters** (no `@Setter`, no `@Data`).
4. **Constructor injection only** — `final` fields + `@RequiredArgsConstructor`.
   Never field/setter `@Autowired`.
5. **Every DTO field has `@JsonProperty("snake_case")`** — wire is snake_case, Java
   is camelCase.
6. **No ambient/technical values in request or response bodies** (tenant_id,
   user_id, scope, correlation id…). They come from the `RequestContext`
   thread-local, not from the client or controller params.
7. **Never pass a context-derived value as a method arg** — the callee reads it
   from `RequestContext` itself. Only exception: the repository boundary (rule 9).
8. **Models store plain `Long` ids, not object graphs** — no `@ManyToOne`/
   `@OneToMany`; join in queries/services.
9. **Every repository method takes `tenantId`** and filters on it — no cross-tenant
   query exists.
10. **Component beans are a public interface + package-protected impl** — every
    injectable component (services, mappers, gateways, strategies…) is a `public
    interface Xxx` with a package-protected `class XxxServiceImpl implements Xxx` in
    the same package. Callers depend on the interface. Excludes controllers,
    `@Configuration`, entities/DTOs, and plain utilities; repositories already
    satisfy it (Spring generates the impl).
11. **Mapping goes through ModelMapper, not by hand** — model↔DTO and DTO↔DTO
    conversion uses a shared, field-access `ModelMapper` bean (no setters, no manual
    builder mapping); customize per type with a `TypeMap`. Mapped destination types
    use non-`final` `private` fields + a no-arg constructor so ModelMapper can
    populate them.

How 6/7/9 reconcile: `RequestContext` (thread-local) carries `tenantId`; the
**service** reads it and passes it into the **repository** — the one and only place
`tenantId` is an explicit parameter. `RequestContextHolder.set()`/`clear()` are
called **only in the filter layer**; everything else just reads. See @AGENTS.md §3.

## Code style (details in @AGENTS.md §5, enforced by `.editorconfig`)

**2-space** indent, **K&R** braces (`) {` on the same line, never on its own line).
Wrap long signatures with the first arg on the next line and multiple args per line —
dense but readable. Core idea: maximize code visible per screen, economizing both
vertical and horizontal space.

## Build / run / test

```
mvnw.cmd clean verify        # compile + test + package (POSIX: ./mvnw)
mvnw.cmd test                # tests
mvnw.cmd spring-boot:run     # run locally
```

Java 25 · Spring Boot 4.0.7 · MySQL · Redis · Kafka · Liquibase · Lombok.
Base package `ai.visitorflow.demo`. Schema via **Liquibase changelogs**, never
`ddl-auto`.

## Context

- **What we're building & why:** `design.md` (architecture, trade-offs, decision
  log). We implement it **partially** — a small, correct, defensible core.
- **Company-neutral:** never put any real company/product name in code, commits,
  README, or the service (per the assignment brief).

## Skills for this repo

- **`/feature-slice`** — scaffold a new versioned feature (controller → service →
  DTOs → repository → entity) that already obeys all 11 rules.
- **`/convention-check`** — review changed code against the 11 rules and report
  violations with fixes.
