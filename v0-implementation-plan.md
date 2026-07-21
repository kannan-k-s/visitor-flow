# v0 Implementation Plan — Experimentation & Assignment Service

> Review draft (rev 6). `design.md` is the full system (the superset); this document is the
> **small, correct, defensible v0 core** we will build from it, under the 11 rules in `AGENTS.md`
> and the existing multi-module layout (`data`, `visitor`, `admin`, `web`).

## Review feedback applied

**rev 6 — the data plane is append-only.** No UPDATEs in the visitor flow. The (✓,✓) identity link
became an `INSERT IGNORE` into a **separate `identity_links` table** (splitting the INSERT-only anon
counter `visitors` from the link), and the `events` consumer uses **`INSERT IGNORE`** instead of
`INSERT … ON DUPLICATE KEY UPDATE`. Every durable data-plane write is now an append.

**rev 5 — anon id = durable MySQL `AUTO_INCREMENT`.** The `anon_visitor_id` is minted by inserting a
row into the MySQL **`visitors`** table — the id space *is* a durable counter, so it survives Redis
(or any cache) loss with no collisions (design §11: Redis stays a rebuildable cache, never the source
of an identity sequence). The mint (one INSERT) happens **only on a visitor's first assign** (no
`X-Anon-Id` yet); every later request replays the anon and writes nothing.

**rev 4 — identity model (both ids optional).** `X-Anon-Id` (numeric `anon_visitor_id`) and
`X-Visitor-Id` (string `visitor_id`) are **both optional, both client-sent**. The backend **always
resolves-or-generates the numeric anon and returns it** (client stores & replays it — design §6.1 /
App. A). Four cases handled (see Identity model). **Hash on the `anon_visitor_id`** (numeric, stable
because durable + client-replayed). Filter resolves for `/assign` (full 4 cases) and `/track`
(lookup-only, design §7).

**rev 2.** 1. `role` removed from `UserEntity`. · 2. `googleSub` removed — OAuth matches on **email**. ·
3. `defaultVariantId` removed from `experiments`; default = `is_default` row in `variants`. ·
4/8. variants/experiments referenced by **PK id** everywhere. · 5. no string `defaultVariantKey` in
the cache (a derived `defaultVariantId` is fine). · 6. Spring Cache `@Cacheable` for tenant
resolution; the hot **config cache = per-tenant Redis hash** (`HMGET` batch, `DEL`-whole-key on
write). · 7. **channel-level views** `*Entity` / `*Request`+`*Response` / `*CacheDto` / `*MqDto`. ·
9. every table has an auto-inc `id` PK (dedup tuple = **unique key**). · 10. `/assign` takes
`List<Long>`. · 11/12. **ids on the wire** — `Map<Long,Long>` (expId→varId). · 13. unknown tenant →
**404 both planes**. · 14. **tenant resolver in `data`**. · 15. seed **tenants + users**.

## Context

Build the requested slice — admin experiment CRUD + results behind authentication, and the anonymous
visitor assign/track data plane — and nothing beyond it. Every choice is traceable to `design.md` and
obeys `AGENTS.md`.

**Confirmed decisions (planning Q&A):**
- **Admin auth:** Google **OAuth2 (OIDC)** login → stateless **JWT in an httpOnly cookie** (design
  §5). **No authorization** (no RBAC / privilege checks).
- **Infra:** **MySQL + Redis + RabbitMQ**. MySQL = source of truth; Redis = config cache + identity
  lookup cache; RabbitMQ = tracking pipeline (one direct exchange → four anon-visitor partitions → `events`).
- **Assignment:** **Hash strategy only** (murmur3 on the `visitor_id` string → bucket → allocation
  range → variant id). **SWRR, sticky bucketing, and login/anon stitching deferred** (interfaces/flags kept).

## In scope

- **Admin (authenticated):** experiment CRUD (create · paginated list · detail-by-id with variants ·
  update · delete); variants a **separate table**, each mapped to exactly one experiment (plain
  `Long` FK, R8), content = static text (`"hai"`, `"hello"`); **results** endpoint (per experiment,
  per variant → assigned / exposed / converted / conversion rate).
- **Visitor (anonymous, fail-safe):** `GET /{tenant}/v1/assign`, `POST /{tenant}/v1/track`.
- **Flow:** DB/cache logic common to both planes lives in **`data`** (tenant resolution, identity
  map, config cache); the **auth/identity filter + Spring Security config live in `web`**.

## Out of scope for v0

SWRR · sticky bucketing · multi-anon-per-`visitor_id` cross-device stitching (v0 keeps one anon per
`visitor_id`) · rate limiting · LLM content · `allowed_domains` / referrer · significance stats ·
tenant/user CRUD & invites (**seeded**).

---

## Identity model (rev 6) — two optional ids, anon minted durably, append-only

- **`anon_visitor_id`** — **numeric**, the canonical per-browser identity. Backend **mints it if
  absent** (INSERT into `visitors` → its `AUTO_INCREMENT` id) and **always returns it** (assign
  response); the client stores it and replays it as `X-Anon-Id`. Durable in MySQL → survives Redis
  loss with no collisions. Hashing and `events` use it.
- **`visitor_id`** — **string**, an optional client key (e.g. a login id), sent as `X-Visitor-Id`.
  When present it is **linked** to an anon by appending to `identity_links`.
- **Both optional.** Resolution (in the filter, design §6.1) — anon-present always wins, **insert-only**:

  | `X-Visitor-Id` | `X-Anon-Id` | action |
  |:---:|:---:|---|
  | – | – | **mint** anon (`INSERT visitors`) → return |
  | – | ✓ | **use** sent anon → return (no write) |
  | ✓ | – | link lookup by (tenant, visitor_id) → hit: use its anon; miss: **mint** (`INSERT visitors`) + **`INSERT identity_links`** → return |
  | ✓ | ✓ | **use** sent anon → return; **`INSERT IGNORE identity_links`** if unlinked (append; no-op if present) |

- **Two tables** (both `id` auto-inc, #9): `visitors(id = anon_visitor_id, tenant_id, created_at)` —
  the durable anon counter, **INSERT-only**; `identity_links(id, tenant_id, visitor_id,
  anon_visitor_id, created_at, UNIQUE(tenant_id, visitor_id))` — the `visitor_id→anon` link,
  **INSERT-only** (idempotent via `INSERT IGNORE` / dup-catch on the unique key, design §6.8). v0
  keeps one anon per `visitor_id`; multi-anon/cross-device deferred. (A raced (✓,–) may mint an extra
  unlinked `visitors` row — harmless waste, design §6.8.)
- **No UPDATEs in the visitor flow** — every durable write is an append (`INSERT` / `INSERT IGNORE`);
  see the append-only note in the visitor module.
- **Write cost:** a mint happens **only on a first assign** (rows 1 & 3-miss). Returning visitors send
  `X-Anon-Id` (row 2) → **no DB write**. Writes scale with new visitors, not requests.
- **Hash uses `anon_visitor_id`** (`murmur3(anon_visitor_id + ":" + experimentId)`) — durable +
  client-replayed, so sticky across restarts and cache loss (design "hashing uses the context anon").
- **`/track` is lookup-only** (design §7): resolve the anon from `X-Anon-Id` / `X-Visitor-Id` without
  minting; no ids → orphan event. (Only `/assign` mints.)

## Wire contract (#10/#11/#12) — ids on the wire

Experiments/variants by **numeric id**; identity via the two optional headers.

```
GET  /{tenant}/v1/assign?experiments=1,2,3    headers: X-Anon-Id?  X-Visitor-Id?   (both optional)
  200 { "assignments": { "1": 44, "2": 91 }, "anon_visitor_id": 78321, "degraded": false }
       // { experimentId : variantId } ; anon_visitor_id always returned for the client to store & replay

POST /{tenant}/v1/track                        headers: X-Anon-Id?  X-Visitor-Id?   (lookup-only; miss → orphan)
  body { "status": "exposed"|"converted", "data": { "1": 44, "2": 91 } }   // { expId : varId }
  200
```

## Channel-level views (#7) and how they map (R11)

| Channel | Type suffix | Package | Examples |
|---|---|---|---|
| DB | `*Entity` | `…model` | `ExperimentEntity`, `VariantEntity`, `EventEntity`, `TenantEntity`, `UserEntity`, `VisitorEntity`, `IdentityLinkEntity` |
| HTTP | `*Request` / `*Response` | `…dto.request` / `…dto.response` | `CreateExperimentRequest`, `ExperimentResponse`, `AssignResponse`, `TrackRequest` |
| Redis | `*CacheDto` | `…cache` | `ExperimentConfigCacheDto` (+ `VariantAllocationCacheDto`) — precomputed/denormalized; stored in the per-tenant `cfg:{tenantId}` hash |
| Message broker | `*MqDto` | `…mq` | `TrackingEventMqDto` |

Every hop crosses views through the shared **ModelMapper** (R11). No view is reused across channels
(the entity is never the broker payload; the cache view may carry derived fields the entity lacks).

---

## Dependencies to add

| Module | Add | Why |
|---|---|---|
| `data` | `com.mysql:mysql-connector-j` (runtime) | MySQL driver (AGENTS §1: pending) |
| `data` | `org.modelmapper:modelmapper` (pinned) | R11 mapping (pending) |
| `data` | `spring-boot-starter-cache` | `@Cacheable` + `RedisCacheManager` for tenant resolution (#6) |
| `visitor` | `com.google.guava:guava` (pinned) | `Hashing.murmur3_32_fixed` (design §6.2); inline murmur3 is the dep-free alt |
| `web` | `spring-boot-starter-oauth2-client` | Google OIDC login (design §5) |

JWT mint/validate uses Spring Security JOSE (`NimbusJwtEncoder`/`Decoder`, HS256 with
`JWT_SIGNING_SECRET`), transitive via `oauth2-client`. Guava + modelmapper versions pinned in the
parent `<dependencyManagement>`.

---

## `data` module — `ai.visitorflow.demo.data`

Shared persistence, domain, request context, **tenant resolution**, the **identity map**, and the
**config cache**. Entities: `@Getter @Builder`, no setters (R3), plain `Long` FKs (R8), non-final
fields + protected no-arg ctor (R11). **Every table has an auto-inc `id` PK (#9).** Every repo method
takes `tenantId` (R9) — sole exception `TenantRepository.findByName`.

### `context/`
- **`RequestContext`** (final, `@Getter @Builder`) — `tenantId`, `userId`, `visitorId` (client
  string key), `anonVisitorId` (numeric, mapped), `correlationId`. Immutable.
- **`RequestContextHolder`** (util) — `ThreadLocal`; `set`/`clear` (web filter only, §3), `get`,
  `tenantId()`, `visitorId()`, `anonVisitorId()`, `userId()`.

### `config/` (`@Configuration`, R10-exempt)
- **`MappingConfig`** — one field-access `ModelMapper` bean (R11 snippet from AGENTS).
- **`CacheConfig`** — `@EnableCaching` + `RedisCacheManager` for `@Cacheable` caches. Cache name:
  `tenant` (tenant-name→id). (#6)
- **`RedisConfig`** — `StringRedisTemplate` for the experiment **config hash** (`HMGET`/`HSET`/`DEL`
  via `HashOperations`), values JSON where needed. (A `visitor_id→anon` Redis lookup cache — design's
  `link:{visitor_id}→anon` — is deferred; `visitor_id`-present requests are the minority.)
- **`TrackingConfig`** — Spring Cloud Stream functional consumer for `tracking-events`; the Rabbit binder
  provisions one direct exchange and four group queues, partitioned by `anonVisitorId`.

### `exception/`
- **`NotFoundException`**, **`ValidationException`** (extend `DomainException`).
- **`ErrorResponse`** — `@Getter @Builder`, `@JsonProperty` snake_case. Rendered by web advice.

### `tenant/` — tenancy, console users, **tenant resolution (#14)**
- **`model/TenantEntity`** — `id`, `name` (unique), `createdAt`.
- **`model/UserEntity`** — `id`, `tenantId`, `email` (unique), `createdAt`. (No `role`/`googleSub` — #1/#2.)
- **`repository/TenantRepository`** — `findByName(String)`.
- **`repository/UserRepository`** — `findByEmail(String)` (login), `findByIdAndTenantId`.
- **`TenantResolver`** (interface, R10) + **`TenantResolverImpl`** — `Long resolve(String tenantName)`;
  **`@Cacheable("tenant")`** over `TenantRepository.findByName`; unknown → `NotFoundException` → **404
  both planes** (#13). Web filter delegates here; context set/clear stays single-owned in web (§3).

### `identity/` — the anon counter + visitor_id link (INSERT-only)
- **`model/VisitorEntity`** (table `visitors`) — `id` (auto-inc PK = **`anon_visitor_id`**, #9 — the
  durable counter), `tenantId`, `createdAt`. **INSERT-only** (mint).
- **`model/IdentityLinkEntity`** (table `identity_links`) — `id` (auto-inc PK, #9), `tenantId`,
  `visitorId` (string), `anonVisitorId` (Long), `createdAt`. Unique `(tenantId, visitorId)`. **INSERT-only**.
- **`repository/VisitorRepository`** — `save(...)` to **mint** an anon (INSERT → auto-inc id). No
  read/update on the `X-Anon-Id`-present path.
- **`repository/IdentityLinkRepository`** — `findByTenantIdAndVisitorId(Long, String)`;
  `insertIgnore(...)` native `INSERT IGNORE` (idempotent append, R9).
- **`VisitorIdentityResolver`** (interface, R10) + **`Impl`** (R4) — the 4-case table, **all inserts**:
  - `long resolveForAssign(Long tenantId, String visitorId?, Long anonId?)` — anon present → use it
    (if `visitorId` present → `insertIgnore` link to it); else `visitorId` present → find link → hit:
    its anon; miss: **mint** (`VisitorRepository.save`) + **`insertIgnore` link** (race → re-read the
    winner, §6.8); else → **mint**. Always returns an anon.
  - `Long resolveForTrack(Long tenantId, String visitorId?, Long anonId?)` — **lookup-only**: sent
    anon, else the linked anon for `visitorId`, else `null` (orphan). No mint, no insert (design §7).

### `experiment/` — experiment/variant persistence **+ the shared config cache**
- **`model/ExperimentEntity`** — `id`, `tenantId`, `name` (admin label; unique per tenant), `strategy`
  (default `"hash"`), `stickyBucketing` (default false), `allowedDomains` (null v0), `createdAt`.
  (No `defaultVariantId` — #3.)
- **`model/VariantEntity`** — `id`, `experimentId` (Long FK, R8), `tenantId`, `isDefault`, `content`,
  `allocPct`. (No `variant_key` — #4/#8.)
- **`repository/ExperimentRepository`** — `findByIdAndTenantId`, `findAllByTenantId(Pageable)`,
  `existsByNameAndTenantId`, `deleteByIdAndTenantId`.
- **`repository/VariantRepository`** — `findAllByExperimentIdAndTenantId`, `deleteAllByExperimentIdAndTenantId`.
- **`cache/ExperimentConfigCacheDto`** (Redis view, #7) — a **precomputed, denormalized read view**
  built once at cache time so `/assign` does pure lookups: `experimentId`, `strategy`,
  **`defaultVariantId`** (derived from the `isDefault` variant), `List<VariantAllocationCacheDto>`;
  **`cache/VariantAllocationCacheDto`** — `variantId`, `allocPct`, `lo`, `hi` (precomputed 10 000-bucket
  range, design §3). May carry any computed/derived value (#5's objection was the string
  `defaultVariantKey`; the derived id is exactly what the cache should hold). Jackson-serializable.
- **`cache/ExperimentConfigCache`** (interface, R10) + **`ExperimentConfigCacheImpl`** (pkg-protected,
  R4) — a **per-tenant Redis hash** `cfg:{tenantId}` (field = experimentId, value = JSON
  `ExperimentConfigCacheDto`) via `HashOperations` (scoped exception to #6):
  - `Map<Long,ExperimentConfigCacheDto> get(Long tenantId, List<Long> experimentIds)` — one **`HMGET`**
    (single round trip, atomic group read — design §6.6/§6.8); nil fields lazily loaded from MySQL
    (experiment + variants → ModelMapper-built DTO, ranges via a `TypeMap`), `HSET` back, `EXPIRE`
    refreshed. Returns resolved configs; absent ids → assign treats as unknown → degraded.
  - `void clear(Long tenantId)` — **`DEL cfg:{tenantId}`**, called on **any** experiment
    create/update/delete: drop the whole tenant key, next assign lazily rebuilds only what it needs.
    Deliberately simple — no per-field `HSET`/`HDEL`, no RENAME swap.
  - Cold-load single-flight (design §11) deferred — a first-touch herd is an accepted v0 risk.

### `event/` — tracking sink (written by consumer, read by results)
- **`model/EventEntity`** — `id` (auto-inc PK, #9), `tenantId`, `experimentId`, `variantId` (Long,
  #4/#8), `anonVisitorId` (Long — logical ref to `visitors.id`; **no enforced FK**, so a spoofed/
  fabricated anon can't error the fail-safe data plane), `status`, `ts`, `degraded`. **Unique key**
  `(tenantId, experimentId, anonVisitorId, status)` for dedup (design App. B).
- **`repository/EventRepository`** — `insertIgnore(...)` native `INSERT IGNORE` on the unique key
  (**append-only** idempotent dedup — keeps the first row, nothing to update since one anon → one
  variant/experiment is an invariant, R9); `findVariantStatusCounts(experimentId, tenantId)` →
  `(variantId, status, count)` excluding degraded assigned.

### `db/changelog/` — Liquibase (never `ddl-auto`)
- `db.changelog-master.yaml` → `001-tenants-users`, `002-experiments-variants`,
  `003-visitors-identity-links`, `004-events`, `005-seed-demo` — **seeds a demo `tenant` row and user
  row(s)** (email-matched, #15) so first Google login succeeds without invite CRUD.

---

## `visitor` module — `ai.visitorflow.demo.visitor` (data plane, fail-safe)

The assignment controller **never throws** on the render path — it degrades with 200. Tracking is
off the render path and returns 503 when the durable broker send fails. **Append-only:** every
durable data-plane write is an `INSERT` / `INSERT IGNORE` — anon mint, `visitor_id` link, and
`events` dedup are all inserts. No UPDATEs, no read-modify-write on the hot path (config-cache `HSET`
backfill is idempotent Redis cache population, not durable state).

### `assignment/`
- **`controller/v1/AssignController`** (R1/R2) — `@GetMapping("/v1/assign")`,
  `@RequestParam("experiments") List<Long> experiments` (#10); one call → `AssignResponse`. No
  tenant/visitor params (R6).
- **`service/AssignmentService`** (interface) + **`AssignmentServiceImpl`** (pkg-protected, R10/R4) —
  numeric `anonVisitorId` from context (R7; resolved/generated in the filter); **cap 20** (design §9);
  one batch `ExperimentConfigCache.get(tenantId, experiments)` (**`HMGET`**); hash on the
  **`anon_visitor_id`**; on strategy error → cache view's `defaultVariantId` + `degraded=true`;
  unknown experiment → omit + `degraded=true`; emit one `assigned` `TrackingEventMqDto`
  (anonVisitorId) per experiment (fire-and-forget). Returns DTO; never throws.
- **`strategy/AssignmentStrategy`** (interface) + **`HashAssignmentStrategyImpl`** (pkg-protected) —
  `String assign(long anonVisitorId, ExperimentConfigCacheDto cfg)`:
  `bucket = murmur3_32(anonVisitorId + ":" + cfg.experimentId()) % 10000` → range lookup → **variantId**.
- **`dto/response/AssignResponse`** (R3/R5) — `@JsonProperty("assignments")` `Map<Long,Long>`
  (expId→varId), `@JsonProperty("anon_visitor_id")` `Long` (always returned), `@JsonProperty("degraded")`.

### `tracking/`
- **`controller/v1/TrackController`** (R1/R2) — `@PostMapping("/v1/track")`,
  `@Valid @RequestBody TrackRequest` → `trackingService.track(req)`; 200 after durable acceptance,
  503 when RabbitMQ is unavailable.
- **`service/TrackingService`** + **`TrackingServiceImpl`** (R10/R4) — `anonVisitorId` from context
  (filter lookup-only; null → no-op orphan, still 200); validate `status ∈ {exposed, converted}`
  (`assigned` server-only); build a `TrackingEventMqDto` per `(expId→varId)` entry (ids straight from
  the client, #11); emit via producer.
- **`producer/TrackingEventProducer`** + **`Impl`** (R10/R4) — sends through one explicit Spring Cloud Stream
  output binding. Sends are serial; a `/track` false/exception becomes `503`, while
  best-effort assigned-event failures are logged and swallowed. No `CompletableFuture` or unmanaged threads.
- **`consumer/TrackingEventConsumer`** + **`Impl`** (R10/R4) — invoked by the functional `Consumer` binding;
  maps `TrackingEventMqDto` → `EventEntity` (ModelMapper) → `EventRepository.insertIgnore` (append-only
  dedup). Writes MySQL `events`.
- **`mq/TrackingEventMqDto`** (broker view, #7) — `tenantId`, `experimentId`, `variantId`,
  `anonVisitorId` (Long), `status`, `ts`, `degraded`.
- **`dto/request/TrackRequest`** (R3/R5) — `@JsonProperty("status")` (validated),
  `@JsonProperty("data")` `Map<Long,Long>` (expId→varId, #12). No ambient fields (R6).

---

## `admin` module — `ai.visitorflow.demo.admin` (control plane)

### `experiment/`
- **`controller/v1/ExperimentController`** (R1/R2) — one call each: `POST /v1/experiments` ·
  `GET /v1/experiments` (`@ParameterObject Pageable`) · `GET /v1/experiments/{id}` ·
  `PUT /v1/experiments/{id}` · `DELETE /v1/experiments/{id}`. No tenant params (R6).
- **`service/ExperimentService`** + **`ExperimentServiceImpl`** (R10/R4) — tenantId from context (R7)
  → repos (R9); **validate** `allocPct` sums to 100 and exactly one `isDefault` (design §9) →
  `ValidationException`; persist experiment + variants transactionally;
  `ExperimentConfigCache.clear(tenantId)` on create/update/delete (drop the whole tenant hash — kept
  simple). Returns DTOs only (R2).
- **`dto/request/CreateExperimentRequest`** (`name`, optional `strategy`, `variants:List<VariantRequest>`),
  **`UpdateExperimentRequest`**, **`VariantRequest`** (`content`, `alloc_pct`, `is_default`). `@Jacksonized`.
- **`dto/response/ExperimentResponse`** (experiment + `List<VariantResponse>`),
  **`ExperimentSummaryResponse`**, **`VariantResponse`** (`id`, `content`, `alloc_pct`, `is_default`),
  **`PagedResponse<T>`** (`items`, `page`, `size`, `total_elements`, `total_pages`). R3/R5.
- **`mapper/ExperimentMapper`** + **`Impl`** (R10/R11) — ModelMapper; seed `tenantId` via builder then
  `map(src,dest)` (AGENTS pattern); `TypeMap` for computed fields. No hand-mapping.

### `results/`
- **`controller/v1/ResultsController`** (R1/R2) — `GET /v1/experiments/{id}/results` → one call.
- **`service/ResultsService`** + **`Impl`** (R10/R4) — `EventRepository.findVariantStatusCounts(id,
  tenantId)` (R9); pivot → per-variant assigned/exposed/converted; `conversionRate = converted/exposed`
  (design §8). Returns DTO.
- **`dto/response/ExperimentResultsResponse`** (`experiment_id`, `List<VariantResultResponse>`),
  **`VariantResultResponse`** (`variant_id`, `assigned`, `exposed`, `converted`, `conversion_rate`).

---

## `web` module — `ai.visitorflow.demo` (runnable app; auth/identity filter + security here)

> **Deviation (per user):** plane filters consolidated in `web`; **tenant + visitor-identity
> resolution live in `data`** (#14/#rev3), the web filter delegates to both. Filters are web entry
> points (R10-exempt); their injected collaborators (`TenantResolver`, `VisitorIdentityResolver`,
> `JwtService`) are R10 beans.

- **`DemoApplication`** — existing `@SpringBootApplication` on `ai.visitorflow.demo` already scans all
  `…demo.*` sub-packages. No change beyond confirming coverage.

### `web/security/`
- **`SecurityConfig`** — `SecurityFilterChain`: `permitAll` for `/*/v1/assign`, `/*/v1/track`,
  `/oauth2/**`, `/login/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`; `authenticated`
  otherwise (admin). `.oauth2Login(...)` + success handler; `JwtCookieAuthenticationFilter` before
  `UsernamePasswordAuthenticationFilter`; `addFilterAfter(RequestContextFilter, …)`. **No role rules.**
- **`OAuthLoginSuccessHandler`** — OIDC success → `UserRepository.findByEmail(email)` (#2/#15); absent
  → 403 (invite-only, design §5); else `JwtService.mint(userId, tenantId, email)` → httpOnly `session`
  cookie (30-min, `Secure`, `SameSite=Lax`) → redirect.
- **`JwtService`** (interface) + **`JwtServiceImpl`** — Nimbus HS256 mint/parse with `JWT_SIGNING_SECRET`.
- **`JwtCookieAuthenticationFilter`** (pkg-protected `OncePerRequestFilter`) — cookie → validate → set
  Spring Security `Authentication` (principal carries userId + tenantId).

### `web/filter/`
- **`RequestContextFilter`** (pkg-protected `OncePerRequestFilter`) — **only place** context is
  set/cleared (§3). Tenant → `data.TenantResolver` (404 if unknown, #13). Then branch:
  - **data-plane assign** → read `X-Anon-Id` + `X-Visitor-Id` (both optional),
    `VisitorIdentityResolver.resolveForAssign` (4-case table → generates anon if absent, links
    `visitor_id`); set `anonVisitorId` (+ `visitorId`).
  - **data-plane track** → `VisitorIdentityResolver.resolveForTrack` (lookup-only; null → orphan).
  - **control-plane** → userId + tenantId from the authenticated principal (+ membership check = isolation).
  `set` → `chain` → `clear` in `finally`.

### `web/config/`
- **`WebMvcConfig`** — `addPathPrefix("/{tenant}", forBasePackage("ai.visitorflow.demo"))` so
  controllers keep `/v1/...` mappings (R1) while serving `/{tenant}/v1/...` (design §4).
- **`OpenApiConfig`** — springdoc metadata.
- **`GlobalExceptionAdvice`** (`@RestControllerAdvice`) — `NotFoundException`→404,
  `ValidationException`→400, auth→403 → snake_case `ErrorResponse` (serves admin; data-plane
  self-handles, fail-safe).

### `web/.../application.properties`
Expand from the one-liner, all via env (`.env.example`): MySQL datasource,
`spring.jpa.hibernate.ddl-auto=none`, `spring.liquibase.change-log=classpath:db/changelog/
db.changelog-master.yaml`, Redis + `spring.cache.type=redis`, RabbitMQ and Spring Cloud Stream bindings,
Google `oauth2.client.registration/provider`, `jwt.signing-secret`, `assign.hash-deadline-ms=100`,
`assign.swrr-deadline-ms=200` (unused v0), `assign.max-experiments=20`.

---

## Testing

- **Hash strategy (unit):** determinism (same `anon_visitor_id`+exp → same variantId, repeated +
  restart semantics); **distribution** — 1e5–1e6 synthetic anons for 50/50 and 90/10, realized split
  within tolerance (design §14); range boundaries (0, 4999, 5000, 9999).
- **Identity resolver (repo test):** the 4 cases, all inserts — (–,–) mints a `visitors` row; (–,✓)
  uses sent anon (no write); (✓,–) mints + `INSERT identity_links`, returns the same anon on repeat;
  (✓,✓) uses sent anon + `INSERT IGNORE` link (no-op if present); concurrent (✓,–) first-link
  collapses to one (unique constraint); `resolveForTrack` returns null for an unseen id (→ orphan).
  Anon survives a simulated Redis flush (it's in MySQL).
- **Validation (unit):** alloc sums to 100; exactly one default → `ValidationException`.
- **Config cache (unit):** `HMGET` returns present configs; nil backfilled from MySQL + `HSET`;
  `clear(tenantId)` `DEL`s the whole `cfg:{tenantId}` key; cache dto ranges + derived `defaultVariantId`.
- **Admin slice (MockMvc):** create→get→list(paged)→update→delete with an authenticated principal;
  validation 400s.
- **Visitor fail-safe (MockMvc):** `/assign` unknown experiment id → 200 `degraded=true` omitted;
  unknown tenant → 404 (#13); no headers → filter generates an anon, returned in `anon_visitor_id`.
- **Consumer dedup (repo test):** duplicate `exposed` → `INSERT IGNORE` keeps one row (append-only, no update).
- Container lifecycle automation via **Testcontainers is deferred**. An opt-in Playwright HTTP E2E
  profile runs against the live local MySQL/Redis/RabbitMQ stack and waits for broker events before
  asserting results; the default build remains unit-focused.

## Verification (end-to-end, manual)

1. `mvnw.cmd clean verify`.
2. Start MySQL/Redis/RabbitMQ; `.env.example`→`.env` (Google creds + `JWT_SIGNING_SECRET`);
   `mvnw.cmd -pl web -am spring-boot:run`. Liquibase creates schema + seeds tenant/user.
3. **Admin:** `/oauth2/authorization/google` → login as seeded email → cookie. `POST /{tenant}/v1/
   experiments` (2 variants "hai"/"hello", 50/50, one default) → note returned variant **ids** →
   list/detail/update.
4. **Assign:** `GET /{tenant}/v1/assign?experiments=<expId>` with no headers → variant **id** +
   generated `anon_visitor_id`; replay that `X-Anon-Id` → **same variant** (sticky); restart → still
   same. Send `X-Visitor-Id` only → anon get-or-created and linked.
5. **Track:** `POST /{tenant}/v1/track {status:"exposed", data:{<expId>:<varId>}}` then `"converted"`
   (echo `X-Anon-Id`) → consumer writes `events`; duplicate → no double count.
6. **Results:** `GET /{tenant}/v1/experiments/{id}/results` → per-variant counts + `conversion_rate`.
7. Swagger `/swagger-ui.html`; health `/actuator/health`.

## Rule-compliance checkpoints (AGENTS §6)

R1 versioned `/v1` in `controller.v1` · R2 thin controllers → DTOs · R3 `@Getter @Builder`, no
setters · R4 `@RequiredArgsConstructor` + final · R5 every DTO field `@JsonProperty` snake_case ·
R6 no ambient values in bodies/params · R7 context not threaded (except repo boundary) · R8 plain
`Long` FKs · R9 every repo method takes+filters `tenantId` (sole exception `findByName`) · R10
interface + pkg-protected impl · R11 ModelMapper field-access, no hand-mapping · §3 set/clear context
only in the web filter · §5 2-space K&R · Liquibase not `ddl-auto` · no company/product name.

## Decisions / deviations log

- **Data plane is append-only (rev 6):** every durable write in the visitor flow is an `INSERT` /
  `INSERT IGNORE` — anon mint (`visitors`), `visitor_id` link (`identity_links`), and `events` dedup.
  No UPDATEs, no read-modify-write on the hot path → no lock contention, replay-safe, scales cleanly.
- **Identity (rev 6):** two optional client headers — `X-Anon-Id` (numeric `anon_visitor_id`) and
  `X-Visitor-Id` (string `visitor_id`). Backend **always resolves-or-mints the anon and returns it**
  (client stores & replays — design §6.1 / App. A); 4-case table in the filter. **Anon = MySQL
  `visitors.id` (`AUTO_INCREMENT`)** — a durable counter, so **Redis loss never resets or collides**
  it (design §11 keeps Redis a rebuildable cache, not an identity source). The `visitor_id→anon` link
  is a separate INSERT-only `identity_links` table (so linking is an append, not an update). Mint =
  one INSERT, **only on a first assign**; returning visitors replay `X-Anon-Id` → no write. **Hash on
  `anon_visitor_id`** (durable + client-replayed → sticky across restarts and cache loss). `/assign`
  mints; `/track` is lookup-only (§7). One anon per `visitor_id` in v0; multi-anon/cross-device + a
  Redis `link:{visitor_id}→anon` cache deferred.
- **Auth/identity filter + security in `web`; tenant + identity resolution in `data`** (#14) —
  DB-touching resolvers sit in `data`; context stays single-owned in the web filter (§3).
- **`ExperimentConfigCache` = per-tenant Redis hash** (#6, refined) — visitor reads via one `HMGET`
  (atomic batch, design §6.6/§6.8); admin `clear(tenantId)` = `DEL` whole key on any write (kept
  simple). Scoped exception to #6's `@Cacheable`; tenant resolution stays `@Cacheable`.
- **Cache view holds precomputed/derived values** — `defaultVariantId`, bucket ranges, etc., built
  once at cache time so the hot path only looks up.
- **ids on the wire** (#10/#11/#12) — experiments/variants by PK id; the visitor identity is the lone string.
- **Guava murmur3** (trusted avalanche impl) with inline as the dep-free alt.
- **Users + tenant seeded, not CRUD'd** (#15) — first Google login matches a seeded email.
- **Hash-only** keeps Redis's v0 role = config + identity cache, RabbitMQ's = tracking pipeline;
  SWRR/sticky deferred.
