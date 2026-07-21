---
name: convention-check
description: Review Java/Spring code in this repository against the 11 project rules and code style in AGENTS.md and report violations with concrete fixes. Use before committing, on a diff, or on named files.
---

# Convention check

Audit code against the 11 rules in `AGENTS.md` §2 and the code style in §5. Default scope: files changed on
the current branch (`git diff --name-only` + staged). If given specific files/dirs,
check those instead.

## How to run
1. Determine scope (changed files, or the paths the user named). Only Java sources
   and `pom.xml`/changelogs are in scope.
2. For each file, check every rule below. Cite `file:line` for each violation.
3. Report as a table: **Rule · file:line · what's wrong · fix**. If clean, say so.
4. Do **not** auto-edit unless asked — report first. When asked to fix, apply the
   minimal change and re-check.

## Checklist (what a violation looks like)

- **R1 Versioning** — controller mapping missing `/v1` (or a version segment);
  controller not in a `controller.v1` package.
- **R2 Thin controller** — controller contains business logic, calls a repository,
  references an `@Entity`, maps entity↔DTO, or a service method returns an entity/model
  instead of a DTO. Controller method should be ~one service call.
- **R3 Builder/Getter** — presence of `@Setter`, `@Data`, or hand-written setters;
  DTO/model missing `@Builder`/`@Getter`.
- **R4 Constructor injection** — any field/setter `@Autowired`; non-`final`
  collaborator field; missing `@RequiredArgsConstructor` (or explicit constructor).
- **R5 JsonProperty** — a DTO field without `@JsonProperty`, or a `@JsonProperty`
  value that isn't `snake_case`.
- **R6 No ambient in bodies/params** — request/response DTO field named `tenantId`/
  `userId`/`scope`/`correlationId`/etc.; controller method accepting tenant/user via
  param, header, or path when it should come from `RequestContext`.
- **R7 No threaded context** — a service/business method takes a parameter that is
  really a `RequestContext` value (e.g. `tenantId`) instead of reading it from
  `RequestContextHolder`. (Repository params are the allowed exception — see R9.)
- **R8 Plain-id models** — entity with `@ManyToOne`/`@OneToMany`/`@ManyToMany`/
  `@OneToOne` or an embedded object graph instead of a plain `Long` FK.
- **R9 Tenant-scoped repos** — a repository method without a `tenantId` parameter, or
  a `@Query` that doesn't filter on `tenant_id`; use of inherited `findById(Long)`
  without tenant scope.
- **R10 Interface + package-protected impl** — an injectable component (`@Service`/
  `@Component` collaborator, incl. mappers/gateways/strategies) declared as a concrete
  `public class` with no interface; an impl class that is `public` instead of
  package-protected, or in a different package from its interface; a collaborator
  injected by its concrete type instead of its interface. (Not applicable to
  `@RestController`, `@Configuration`, entities/DTOs, utilities, or Spring Data repos.)
- **R11 ModelMapper mapping** — hand-written mapping between model/DTO types (a chained
  `.builder()....build()` that copies fields across types, or `dto.setX(model.getX())`) instead
  of the shared `ModelMapper` bean; a mapper that doesn't inject `ModelMapper`; a mapped
  destination type using `final` fields or missing a no-arg constructor (ModelMapper can't
  populate it).
- **Context lifecycle (§3)** — `RequestContextHolder.set(...)` or `.clear()` called anywhere
  outside the filter layer (`common/web`). Only the filter owns the thread-local's lifespan.
- **Code style (§5)** — 4-space or tab indentation instead of 2 spaces; Allman braces (`{` on
  its own line); one-argument-per-line method signatures.
- **Bonus** — schema change via `ddl-auto` instead of a Liquibase changelog; any real
  company/product name in code, comments, or commit messages (must stay neutral).

## Report format
```
| Rule | Location            | Issue                                   | Fix |
|------|---------------------|-----------------------------------------|-----|
| R5   | XxxResponse.java:14 | field `displayName` missing @JsonProperty | add @JsonProperty("display_name") |
| R9   | XxxRepository.java:9| findById(Long) has no tenantId          | use findByIdAndTenantId(id, tenantId) |
```
End with a one-line verdict: pass, or N violations across M files.
