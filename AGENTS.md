# Engineering Guide & Conventions

> Canonical, tool-agnostic engineering standard for this repository.
> Every contributor — human or AI (Claude Code, Cursor, Copilot, …) — MUST
> follow it. Claude Code loads it via `@AGENTS.md` from `CLAUDE.md`.

---

## 1. What this service is

A multi-tenant **Experimentation & Assignment Service**. On every page load it
answers one question — *"which variant should this visitor see right now?"* — and
later records exposures/conversions so it can report which variant wins.

The full architecture, trade-offs, and decision log live in **[`design.md`](./design.md)**.
Read it before touching the assignment engine, tracking, or storage. We are
implementing this **partially, not fully** — build the core, defend every choice.

Two planes with opposite requirements:

- **Data plane** (hot, public, browser-facing): `/{tenant}/v1/assign`, `/{tenant}/v1/track`.
  Latency-critical, **anonymous (no auth)**, rate-limited, **fail-safe (never breaks the page)**.
- **Control plane** (warm, private, human-facing): experiment CRUD + results
  dashboard. OAuth + RBAC.

**Stack:** Spring Boot 4.0.7 · Java 25 · MySQL (source of truth) · Redis (cache) ·
RabbitMQ via Spring Cloud Stream (async tracking) · Liquibase (schema) · Spring Security · Lombok ·
ModelMapper (object mapping).

> Stack notes: schema changes go through **Liquibase changelogs**, never
> `spring.jpa.hibernate.ddl-auto`. Two runtime deps are **not yet in `pom.xml`** —
> add them when needed: **`mysql-connector-j`** (MySQL driver) and
> **`org.modelmapper:modelmapper`** (R11).

---

## 2. The Rules (non-negotiable)

Enforced in review and by the `convention-check` skill. Each rule has a ✅ do /
❌ don't so there is no ambiguity.

### R1 — All APIs are versioned
Every public path starts with a version segment. Controllers live in a `...controller.v1`
package. Bump to `v2` for breaking changes; never break `v1` in place. Public paths
additionally carry a leading `/{tenant}` segment that the filter resolves and consumes
(§3) — e.g. `/{tenant}/v1/experiments` — so controller mappings still begin at the version.

```java
✅ @RestController
   @RequestMapping("/v1/experiments")        // versioned, plural resource
   class ExperimentController { … }

❌ @RequestMapping("/experiments")            // unversioned
```

### R2 — Controllers only call a service; services return DTOs
A controller binds/validates input, calls **one** service method, returns its DTO.
No business logic, no repository access, no entity/model mapping, no branching on
domain state in the controller. **Services return DTOs, never entities/models.**

```java
✅ @PostMapping
   ExperimentResponse create(@Valid @RequestBody CreateExperimentRequest req) {
     return experimentService.create(req);   // one call, returns a DTO
   }

❌ // repository calls, mapping, or entities in the controller
   ExperimentEntity e = repo.save(...); return e;
```

### R3 — `@Builder` + `@Getter`, never setters
DTOs and models are built, not mutated. Use Lombok `@Builder` and `@Getter`.
Do **not** add `@Setter` or `@Data` (`@Data` implies setters). Immutability comes
from exposing **no setters** — not from `final`; fields stay non-`final` `private`
so JPA and ModelMapper (R11) can populate them.

```java
✅ @Getter
   @Builder
   @NoArgsConstructor(access = AccessLevel.PRIVATE)    // lets ModelMapper instantiate (R11)
   @AllArgsConstructor(access = AccessLevel.PRIVATE)   // @Builder needs it alongside @NoArgsConstructor
   public class ExperimentResponse {
     @JsonProperty("control_variant_id")
     private Long controlVariantId;                     // private, non-final, no setter
   }

❌ @Data                                                 // brings setters
   public class ExperimentResponse { … }
```

### R4 — Constructor injection only
No field/setter `@Autowired`. Depend on `final` fields populated by a constructor —
use Lombok `@RequiredArgsConstructor`. This keeps beans testable and immutable.

```java
✅ @Service
   @RequiredArgsConstructor
   class ExperimentServiceImpl implements ExperimentService {   // package-protected impl (R10)
     private final ExperimentRepository experimentRepository;
     private final ModelMapper modelMapper;
   }

❌ @Autowired private ExperimentRepository repo;        // field injection
```

### R5 — DTO fields carry `@JsonProperty` snake_case
Everything crossing the wire to/from a client is **snake_case**; Java stays
**camelCase**. Annotate **every** DTO field explicitly with `@JsonProperty("snake_case")`.
(A global `SNAKE_CASE` Jackson strategy may be set as a safety net, but per-field
annotation is the rule — explicit and survives config changes.)

```java
✅ @JsonProperty("control_variant_id")
   private Long controlVariantId;

   @JsonProperty("sticky_bucketing")
   private boolean stickyBucketing;

❌ private Long controlVariantId;                       // no annotation → camelCase leaks
```

### R6 — No technical/ambient values in request or response bodies
`tenant_id`, `user_id`, auth scope, correlation id, `degraded`, and similar
**ambient** values are never accepted in a request DTO nor added as controller
method params. They are resolved from the authenticated principal into a
**`RequestContext`** (thread-local) by the filter (§3). Request/response bodies
carry only genuine business fields.

```java
✅ CreateExperimentRequest { name, variants, allocation, strategy, … }   // no tenant_id
   // tenantId comes from RequestContextHolder inside the service

❌ CreateExperimentRequest { tenantId, name, … }        // client must not supply tenant
❌ ExperimentResponse create(@RequestHeader Long tenantId, …)            // ambient in signature
```

### R7 — Don't pass context-derived values as method arguments
If a value comes from `RequestContext`, the callee reads it from context itself —
do **not** thread it through parameters. This prevents "passed the wrong tenant"
bugs and keeps signatures about business intent. **The one exception is the
repository boundary (R9).**

```java
✅ // service method takes no tenantId; reads it where needed
   public ExperimentResponse get(Long experimentId) {
     Long tenantId = RequestContextHolder.tenantId();          // read at the boundary
     return experimentRepository.findByIdAndTenantId(experimentId, tenantId)
       .map(experimentMapper::toResponse)
       .orElseThrow(() -> new NotFoundException("experiment", experimentId));
   }

❌ public ExperimentResponse get(Long tenantId, Long experimentId) { … }  // threading context
```

### R8 — Models store plain ids, not object graphs
JPA entities hold foreign keys as plain `Long` (`experimentId`, `controlVariantId`),
**not** `@ManyToOne`/`@OneToMany` associations. No lazy-loading surprises, no N+1,
no mapping complexity inside models. Join in queries/services when needed.

```java
✅ @Entity
   class VariantEntity {
     @Id Long id;
     Long experimentId;      // plain FK
     Long tenantId;
   }

❌ @ManyToOne private ExperimentEntity experiment;      // object graph in the model
```

### R9 — Every repository method takes `tenantId`
Tenant isolation is enforced at the data layer. Every finder/mutator includes
`tenantId` (conventionally the last argument) and every query filters on it. There
is **no** repository method that can read/write across tenants. The service supplies
`tenantId` from `RequestContext` at this single boundary (the R7 exception).

```java
✅ interface ExperimentRepository extends JpaRepository<ExperimentEntity, Long> {
     Optional<ExperimentEntity> findByIdAndTenantId(Long id, Long tenantId);
     List<ExperimentEntity> findAllByStateAndTenantId(String state, Long tenantId);
   }

❌ Optional<ExperimentEntity> findById(Long id);        // no tenant scope
```

### R10 — Component beans are a public interface + package-protected impl
Every injectable Spring component — services **and all other collaborator beans**
(mappers, gateways/clients, strategies, engines, caches; anything another bean
depends on) — is a **public interface** with a **package-protected implementation**
(`XxxServiceImpl`, no `public` modifier) in the **same package**, carrying the
stereotype annotation. Callers depend only on the interface; the impl stays hidden.

Excluded (not injectable collaborators): `@RestController` (web entry points),
`@Configuration` classes, entities/DTOs/records, and plain utilities like
`RequestContextHolder`. Spring Data repositories already satisfy this — interface
only, Spring generates the impl.

```java
✅ public interface ExperimentService {                  // public contract
     ExperimentResponse create(CreateExperimentRequest request);
   }

   @Service                                               // impl is package-protected
   @RequiredArgsConstructor
   class ExperimentServiceImpl implements ExperimentService {
     private final ExperimentRepository experimentRepository;
     …
   }

❌ @Service
   public class ExperimentService { … }                   // concrete public bean, no interface
```

### R11 — Object mapping goes through ModelMapper (field access), not by hand
Model↔DTO and DTO↔DTO conversion uses the **ModelMapper** library through one
shared, configured `ModelMapper` bean — never hand-written `set…`/builder/field
assignment. Per-type **customization** (renames, computed fields, skips) is a
`TypeMap`/`Converter` registered in the mapper impl; do not drop back to manual
mapping. Mappers still obey R10 (public `XxxMapper` interface + package-protected
`XxxMapperImpl`) and inject `ModelMapper` by constructor (R4).

Mechanics (so it coexists with R3's no-setters rule):

- The `ModelMapper` bean is configured for **field access** so it reads/writes
  **private fields** reflectively — **no setters** needed.
- Because it writes fields, **mapped destination types use non-`final` `private`
  fields** and a **no-arg constructor** it can instantiate (entities:
  `@NoArgsConstructor(access = PROTECTED)`, also required by JPA; response DTOs:
  `@NoArgsConstructor(access = PRIVATE)`).

```java
// data/config — one configured, field-access ModelMapper shared by all modules
@Configuration
class MappingConfig {
  @Bean
  ModelMapper modelMapper() {
    ModelMapper mm = new ModelMapper();
    mm.getConfiguration()
      .setFieldMatchingEnabled(true)
      .setFieldAccessLevel(AccessLevel.PRIVATE);   // org.modelmapper.config.Configuration.AccessLevel
    return mm;
  }
}
```
```java
✅ @Component
   @RequiredArgsConstructor
   class ExperimentMapperImpl implements ExperimentMapper {
     private final ModelMapper modelMapper;
     @Override
     public ExperimentResponse toResponse(ExperimentEntity e) {
       return modelMapper.map(e, ExperimentResponse.class);   // customize via a TypeMap
     }
   }

❌ ExperimentResponse.builder().name(e.getName())…    // hand-mapping instead of ModelMapper
❌ response.setName(e.getName());                      // setters (also violates R3)
```

---

## 3. Request context (how R6, R7, R9 fit together)

Ambient request data lives in a thread-local `RequestContext`, populated once per
request by the servlet filter — the tenant from the `{tenant}` segment of the URL
path on **both** planes, the visitor id from a request header on the data plane
(anonymous, no auth), and the user from the OAuth session on the control plane.

```java
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequestContext {
  private final Long tenantId;        // always present after auth
  private final Long userId;          // control plane only
  private final String visitorId;     // data plane only
  private final Set<String> scopes;   // control-plane RBAC privileges
  private final String correlationId;
}

public final class RequestContextHolder {
  private static final ThreadLocal<RequestContext> CTX = new ThreadLocal<>();
  private RequestContextHolder() {}
  public static void set(RequestContext c) { CTX.set(c); }
  public static RequestContext get() {
    RequestContext c = CTX.get();
    if (c == null) throw new IllegalStateException("RequestContext not initialised");
    return c;
  }
  public static Long tenantId() { return get().getTenantId(); }
  public static void clear() { CTX.remove(); }
}
```

**`set()` and `clear()` are invoked in the filter layer only.** Nothing else in the
app sets or clears the context — controllers and services read it (`get()` /
`tenantId()`) and never mutate its lifecycle. This is the single owner of the
thread-local's lifespan and guarantees it is always cleared (leak-free thread reuse).

```java
// filter layer (visitor.web / admin.web) — the ONLY place RequestContext is set or cleared
@Component
@RequiredArgsConstructor
class RequestContextFilter extends OncePerRequestFilter {
  private final PrincipalResolver principalResolver;   // tenant (from path) + identity → RequestContext

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response, FilterChain chain
  ) throws ServletException, IOException {
    try {
      RequestContextHolder.set(principalResolver.resolve(request));   // set — here only
      chain.doFilter(request, response);
    } finally {
      RequestContextHolder.clear();                                   // clear — here only
    }
  }
}
```

Data flow: **filter sets context → service reads context → service passes tenantId
to the repository (only there, R9) → filter clears context.** Controllers never see
tenantId; services never take it as a param; repositories always require it.

---

## 4. Package layout

**Multi-module Maven build** (`design.md` §2) — the module split follows the plane
boundary; assembled into one deployable (`web`) now. Base group `ai.visitorflow`,
base package `ai.visitorflow.demo`.

```
demo (parent · pom)
├── data ──────────  shared persistence + domain; both planes depend on it, it depends on neither
│   └── ai.visitorflow.demo.data
│       ├── context/          RequestContext, RequestContextHolder
│       ├── config/           @Configuration: MappingConfig/ModelMapper, Redis, DataSource
│       ├── exception/        domain exceptions + error DTOs
│       └── <feature>/
│           ├── model/        JPA entities (plain Long FKs — R8)
│           └── repository/   tenant-scoped repositories (R9)
├── visitor ───────  data plane (anonymous, hot, fail-safe); depends on data
│   └── ai.visitorflow.demo.visitor
│       ├── web/              data-plane filter (tenant + anon/visitor → context), rate limiting
│       ├── assignment/       /{tenant}/v1/assign — strategies (hash | swrr), fallback
│       └── tracking/         /{tenant}/v1/track  — one endpoint {status, data}, idempotency, producer
├── admin ─────────  control plane (OAuth + RBAC, consistent); depends on data
│   └── ai.visitorflow.demo.admin
│       ├── web/              control-plane filter (tenant + OAuth session → context), @ControllerAdvice
│       ├── experiment/       experiment CRUD & lifecycle
│       └── results/          per-experiment aggregates + validity
└── web ───────────  runnable app; depends on admin + visitor (only module that repackages)
    └── ai.visitorflow.demo
        ├── DemoApplication   @SpringBootApplication — component-scans every module
        └── web/{security,filter,config}   OAuth login + JWT · filter registration/order · OpenAPI
```

Each plane feature (`assignment`, `tracking`, `experiment`, `results`) is layered inside:
`controller/v1/ · service/ · dto/{request,response}/ · mapper/`. A **vertical slice spans
two modules**: entity + repository live in `data.<feature>` (R8/R9); the
controller/service/DTOs/mapper live in the owning plane — `visitor.<feature>` or
`admin.<feature>`. Mapping (entity ↔ DTO) is a `mapper` (R10) delegating to ModelMapper
(R11), invoked from the **service** — never in an entity (R8) or a controller (R2).

---

## 5. Code style

**Guiding principle:** maximize the code readable on one screen — economize **both**
vertical and horizontal space, without sacrificing readability. Prefer fewer, denser
lines over sparse ones; wrap only when a line genuinely gets too long (~120 cols).

- **Indentation: 2 spaces.** Never tabs, never 4. (Enforced by `.editorconfig`.)
- **Braces: K&R** — opening brace on the same line; `else`/`catch`/`finally` on the
  same line as the preceding `}`. Never Allman (brace on its own line).

  ```java
  public void method() {
    if (x) {
      doThing();
    } else {
      doOther();
    }
  }
  ```
  ```java
  // ❌ Allman — do not do this
  public void method()
  {
  }
  ```

- **Wrapping long signatures / calls:** break after `(`, put the **first argument on
  the next line**, pack **multiple arguments per line** (never one-arg-per-line), and
  close with `) {` (or `)`) on its own line.

  ```java
  ✅ public VariantDecision assign(
       Identity identity, Experiment experiment,
       Deadline deadline
     ) {
       …
     }

  ❌ public VariantDecision assign(Identity identity,       // one arg per line —
                                   Experiment experiment,    // wastes vertical space
                                   Deadline deadline) { … }
  ```

---

## 6. Definition of done

Before a change is "done":

- [ ] Path versioned (`/v1/...`), controller in `controller.v1` (R1)
- [ ] Controller is one service call returning a DTO; no logic/entities (R2)
- [ ] DTOs/models use `@Builder @Getter`, no setters/`@Data` (R3)
- [ ] Constructor injection via `@RequiredArgsConstructor`, `final` deps (R4)
- [ ] Every DTO field has `@JsonProperty("snake_case")` (R5)
- [ ] No ambient values in bodies or controller params; from `RequestContext` (R6)
- [ ] No context value threaded through method args, except the repo boundary (R7)
- [ ] Entities use plain `Long` FKs, no associations (R8)
- [ ] Every repository method takes and filters on `tenantId` (R9)
- [ ] Component beans are a public interface + package-protected impl (R10)
- [ ] Mapping goes through ModelMapper, not by hand; `set`/`clear` context only in filter (R11, §3)
- [ ] 2-space indent, K&R braces, dense-but-readable wrapping (§5)
- [ ] Schema change is a Liquibase changelog (not `ddl-auto`)
- [ ] `mvnw clean verify` passes; new logic has tests
- [ ] **No company/product name anywhere** (repo, code, commits, README — per brief)

---

## 7. Build & test

Maven wrapper (Windows shown; use `./mvnw` on POSIX):

```
mvnw.cmd clean verify        # compile + test + package
mvnw.cmd test                # unit/integration tests
mvnw.cmd spring-boot:run     # run locally
```
