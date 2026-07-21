# v0 Implementation Review — codex output vs. `v0-implementation-plan.md` + `AGENTS.md`

> **Reviewer:** Claude Code · **Date:** 2026-07-22
> **Scope:** the uncommitted working tree on top of `a70faa8` implementing `v0-implementation-plan.md`.
> **Nature of this review:** documentation only — **no code was changed.** Each item below is a
> proposal for you to accept/reject before anything is implemented.

> **Post-review resolution:** the live implementation now uses Spring Cloud Stream's Rabbit binder,
> with `StreamBridge`, a functional `Consumer`, one direct exchange, and four binder-provisioned
> partition queues. There is no `RabbitTemplate`, `@RabbitListener`, hand-written broker topology,
> DLQ, or AMQP starter. The infra README, implementation plan, engineering guides, and application
> properties have been reconciled. Findings below are retained as the review's historical snapshot.

## ⚠️ Snapshot caveat (important)

codex is **editing live** while this review was written. I caught this mid-review: the tracking
layer was **Kafka** when I first read it and had been rewritten to **RabbitMQ** by the time I
re-checked (new `RabbitConfig.java` at ~00:59, poms + `application.properties` at ~01:04). So:

- Treat every finding as a **snapshot**. Re-verify against the tree before acting.
- Anything about the messaging broker, poms, or `application.properties` is only as of ~01:05.
- I **dropped** two findings that codex had already fixed while I watched (see *§7 Withdrawn*).

## 1. Overall assessment

Strong, faithful implementation of the plan. The 11 rules are followed almost everywhere; the
module boundaries (`data` ← `visitor`/`admin` ← `web`) match `AGENTS.md §4`; the append-only
identity model, tenant-scoped repositories, and per-tenant Redis config hash are all in place.

- **Build:** `mvnw -o clean compile` → **BUILD SUCCESS** across all four modules.
- **Tests present:** hash distribution/determinism, identity resolver (4 cases), config-cache
  bucket ranges, results conversion-rate, JWT/OAuth-state round-trip, tenant-in-state resolver.
- The implementation is in several places **more complete than the plan** (variant diff on update,
  orphan-conversion accounting, tenant-scoped user lookup, publisher-confirm durability). Those are
  improvements, but a few of them **change the plan's contract** — flagged below so you decide.

The findings are mostly **"confirm this deliberate deviation"** and **doc/stale-artifact cleanup**,
not bugs. Severity legend: 🔴 decide before merge · 🟡 should fix · 🟢 nit.

---

## 2. Deviations from the plan — please confirm intent

### 2.1 🔴 `/track` is now **durable (can return 503)**, not fail-safe-200
- `visitor/.../tracking/service/TrackingServiceImpl.java:33` calls `sendDurably(...)`, and
  `producer/TrackingEventProducerImpl.java:56,60,64` throw `TrackingUnavailableException` when the
  broker nacks / returns / times out. `web/.../error/GlobalExceptionAdvice.java:44` maps that to
  **HTTP 503**.
- The plan says `/track` is **"200 (fail-safe)"** with the producer doing **"async send,
  drop-and-count on failure"** (plan §visitor/tracking, §Decisions). `design.md`'s data plane
  "never breaks the page."
- **This is a deliberate, defensible trade** (durability of exposed/converted vs. corrupting results
  by silently dropping conversions), and the split is thoughtful: `/assign`'s `assigned` events stay
  **best-effort** (render path preserved), only `/track` is durable. The `application.properties`
  comment even documents it. **But it contradicts the written plan/`design.md` contract.**
- **Decision needed:** keep durable-`/track` (and update the plan + design note), or revert `/track`
  to fire-and-forget 200. My recommendation: **keep it**, update the docs.

### 2.2 🟡 Data-plane endpoints throw `400` on malformed input (not degrade-200)
- `/assign`: `AssignmentServiceImpl.java:70-80` throws `ValidationException` (empty list, `> max`,
  non-positive id) → 400. `RequestContextFilter.java:99` throws `ValidationException` for a
  malformed `X-Anon-Id` → 400. A missing `experiments` param → Spring 400.
- The plan says data-plane controllers **"never throw on the render path — degrade / no-op with
  200."** Returning 400 for a broken integration is arguably *better*, but it's a deviation from the
  stated fail-safe contract.
- **Decision needed:** are 4xx-on-bad-input acceptable for the data plane, or must these degrade to
  200? (I lean: 4xx for genuinely malformed requests is fine; document the refinement.)

### 2.3 🟡 First-touch `/assign` hard-depends on MySQL (unguarded 500)
- `RequestContextFilter.java:36-52` only catches `NotFound/Validation/Forbidden`. The identity mint
  (`VisitorIdentityResolverImpl.mint` → `visitors` INSERT) runs **before** context is set; if MySQL
  is down, a generic `DataAccessException` propagates uncaught → **500** for a first-time visitor.
- By contrast, cache/DB failure *after* identity resolves **is** handled fail-safe:
  `AssignmentServiceImpl.java:36-40` wraps `configCache.get(...)` and returns `degraded=true` 200.
- This is inherent to "anon = durable MySQL `AUTO_INCREMENT`" (plan §rev5) — returning visitors who
  send `X-Anon-Id` don't write and are unaffected. Worth a conscious note: the render path is *not*
  fully fail-safe for brand-new visitors during a DB outage.

### 2.4 🟢 `ExperimentConfigCache.get(...)` dropped the `tenantId` parameter
- Plan wrote `get(Long tenantId, List<Long> experimentIds)`; impl is `get(List<Long>)` and reads
  `RequestContextHolder.tenantId()` internally (`ExperimentConfigCacheImpl.java:38`).
- This is **more** R7-compliant than the plan (callee reads context; tenant only crosses the repo
  boundary). Fine — just calling out that the signature differs from the plan text.

### 2.5 🟢 Login looks up user by `(email, tenantId)`, not `email`
- Plan §admin/web said `UserRepository.findByEmail(email)`. Impl uses
  `findByEmailAndTenantId(...)` (`UserRepository.java:8`, `OAuthLoginSuccessHandlerImpl.java:39`),
  with the tenant recovered from the signed OAuth `state`. Correct for multi-tenant (same email can
  exist per tenant, matching `uk_users_tenant_email`). Improvement — just update the plan.

### 2.6 🟢 Extra fields/endpoints beyond the plan
- Results carries `orphan_converted` and the query does orphan-conversion filtering
  (`EventRepository.java:26-66`, `ExperimentResultsResponse.java:22`) — beyond the plan's simple
  pivot. Good addition; confirm it's wanted in the v0 surface.
- `PUT /experiments/{id}` does a full **variant add/update/delete diff** with an
  `analytics_warning` when events exist (`ExperimentServiceImpl.java:78-114`) — richer than the
  plan's "persist variants." Good, but more surface to test.

---

## 3. Convention (11-rule) compliance

Overall **high**. Table, then the few borderline spots.

| Rule | Verdict | Notes |
|---|---|---|
| R1 versioned `/v1` in `controller.v1` | ✅ | all controllers |
| R2 thin controllers → DTO | ✅ | one service call each |
| R3 `@Builder @Getter`, no setters | ✅ | entities protected no-arg; DTOs `@Jacksonized` |
| R4 constructor injection | ✅ | `@RequiredArgsConstructor`/explicit ctors, `final` |
| R5 `@JsonProperty` snake_case | ✅ | every wire DTO field |
| R6 no ambient values in bodies/params | ✅ | tenant/anon from context |
| R7 no context value as arg (except repo) | ⚠️ | one deviation — see 3.1 |
| R8 plain `Long` FKs | ✅ | no associations |
| R9 repo methods take `tenantId` | ✅* | *see 3.3 (inherited `JpaRepository` methods) |
| R10 interface + pkg-protected impl | ✅ | services/mappers/gateways/strategies/resolvers |
| R11 mapping via ModelMapper | ⚠️ | compliant but borderline — see 3.2 |

### 3.1 🟡 R7 — assignment strategy receives a context-derived value as an argument
- `AssignmentServiceImpl.java:54` reads `anonVisitorId` from context and **passes it** into
  `AssignmentStrategy.assign(long anonVisitorId, cfg)` (`strategy/AssignmentStrategy.java:7`).
- Strictly R7 says the callee should read it from context itself. Here it's threaded so the strategy
  stays a **pure, unit-testable function** (`HashAssignmentStrategyImplTest` calls `assign(918273L, exp)`).
- **Recommendation:** keep the pure signature (testability wins), and note it as a sanctioned R7
  exception for stateless strategies — or have the strategy read context (loses the clean test).
  Your call; I'd keep it and document the carve-out.

### 3.2 🟡 R11 — computed mappings are effectively hand-built inside a `Converter`
- `data/.../cache/ExperimentConfigMapperImpl.java:21-47` and the admin/results/assignment mapper
  impls assemble the destination with Lombok builders inside a `Converter`/helper rather than
  letting ModelMapper map fields. R11 *permits* a `Converter` for computed fields (bucket ranges,
  conversion rate) — so this is **within the rule** — but for the config-cache mapper the *entire*
  object is built by hand, which is close to the "no manual builder mapping" line.
- Acceptable given the heavy computation (10k-bucket ranges, derived `default_variant_id`). Flagging
  so it's a conscious choice, not an accident. No change strictly required.

### 3.3 🟢 R9 — custom finders are tenant-scoped; inherited `JpaRepository` methods are not
- Every *declared* method takes `tenantId` (or is the documented `findByName` exception). But
  extending `JpaRepository` still exposes `findById/findAll/deleteById/...` which are **not**
  tenant-scoped and could be misused later. Same for all repos; matches the plan's approach. Latent
  risk only — consider a note in `AGENTS.md` that inherited finders are off-limits.

### 3.4 🟢 Mappers read `RequestContext`
- `TrackingMapperImpl.java:25-27` and `ExperimentMapperImpl.java:29,42` pull `tenantId`/`anonVisitorId`
  from the holder. R6/R7-compliant, but context reads in a *mapper* are a mild smell — a service is
  the more natural boundary. Cosmetic.

---

## 4. Correctness / logic notes (no bugs found, but verify)

- 🟢 **Event dedup key excludes `variant_id`** (`EventEntity.java:18-20`,
  `uk_events_dedup = (tenant, experiment, anon, status)`). Correct given the "one anon → one variant"
  invariant; the update path's `analytics_warning` covers the mid-experiment-change edge. Fine.
- 🟢 **Bucket math is exact.** `alloc_pct` is `DECIMAL(5,2)`, `@Digits(fraction=2)`,
  `@DecimalMin("0.01")`; `movePointRight(2).setScale(0, UNNECESSARY)` can't throw for DB-sourced
  values, and validated sum = 100.00 fully tiles buckets 0..9999. Out-of-range hash → strategy
  throws → caught → default variant + degraded. Solid.
- 🟢 **Cache invalidation is commit-safe.** `ExperimentConfigCacheImpl.clear()` registers an
  `afterCommit` synchronization (`:51-64`) so Redis is only evicted after the DB commit. Nice.
- 🟢 **Redis-down resilience on `/assign`:** `readRedis` swallows its own exceptions and falls back
  to MySQL; `writeRedis` swallows too. Good.
- 🟢 **Publisher confirms wired correctly:** `publisher-confirm-type=correlated`,
  `publisher-returns=true`, `template.mandatory=true` all set, and `sendDurably` checks both
  `ack()` and `getReturned()`. Consistent.

---

## 5. Stale artifacts & doc drift (resolved after this snapshot)

These findings described the intermediate tree and have been resolved:

- `infra/README.md:21-22,46-67` still documents **Kafka** (SASL, `kafka-ui`, `apache/kafka` image,
  `spring.kafka.*` props) while `infra/docker-compose.yml` runs **RabbitMQ**. The service table is
  wrong (lists kafka/kafka-ui rows that don't exist).
- `infra/.gitignore:5-6` and `infra/kafka/data/.gitkeep` are Kafka leftovers (no Kafka service).
- `v0-implementation-plan.md` still specifies **Kafka** end-to-end (Dependencies table,
  `KafkaConfig`, `@KafkaListener`, topic/key wording). The plan should be reconciled to RabbitMQ (or
  a decision-log entry added), since it's the spec this is reviewed against.
- **Resolution:** Spring Cloud Stream was selected. The visitor module uses
  `spring-cloud-starter-stream-rabbit`; the producer uses `StreamBridge`; the consumer is a functional
  `Consumer<TrackingEventMqDto>`; binder properties provision the direct exchange and four queues.
- 🟢 Local-dev friction: `application.properties` uses `username=root` +
  `password=${RABBITMQ_PASSWORD}`; compose sets `root/root`, but `.env.example` ships
  `RABBITMQ_PASSWORD=change-me`. Note that it must be `root` to match compose.

---

## 6. Minor / nits (🟢)

- **Unused imports:** `ExperimentConfigCacheImpl.java:8,9,13` (`Duration`, `ArrayList`, `Objects`)
  and `CacheConfig.java:3` (`Duration`). Harmless; would trip a stricter checkstyle.
- **Seed won't match a real login:** `005-seed-demo.yaml:23` seeds `admin@example.com`; a real Google
  login will 403 until the seeded email is changed to the tester's Google address. Expected for a
  demo — worth a line in the run instructions.
- **Tenant enumeration before auth:** `RequestContextFilter` resolves the tenant (404 on unknown)
  *before* Spring's authorization check runs, so an unauthenticated caller can distinguish
  valid/invalid tenants via 404-vs-401 on admin paths. Low-risk info leak; acceptable for v0.
- **CSRF disabled** (`SecurityConfig.java:28`) with a cookie-borne JWT — mitigated by
  `SameSite=Lax` on the session cookie. Fine for v0/demo; note it if the admin UI grows.
- **Filter bean name** `@Component("visitorRequestContextFilter")` on the *shared* both-planes
  filter reads oddly ("visitor"). Cosmetic.

---

## 7. Withdrawn during review (codex fixed these live — no action)

- ~~Messaging won't compile — code is Kafka, config/poms are RabbitMQ.~~ **Resolved**: fully migrated
  to RabbitMQ (`RabbitConfig`, `RabbitTemplate`, `@RabbitListener`, `TrackingProperties` = exchange/
  routingKey/queue) and the reactor compiles green.
- ~~`/{tenant}/v1/auth/login` never triggers the Google redirect.~~ **Not a bug**: `TenantOAuthLoginFilter`
  sets a request attribute that makes `TenantAuthorizationRequestResolverImpl.resolve` force the
  2-arg `delegate.resolve(request, "google")`, so the redirect fires and the tenant rides in the
  signed `state`. Clever; keep it.

---

## 8. Suggested next steps (for after your review)

1. **Decide 2.1** (durable vs. fail-safe `/track`) and **2.2** (4xx vs degrade on bad input) — these
   set the data-plane contract; reconcile plan + `design.md` to the decision.
2. **Reconcile docs to RabbitMQ:** `v0-implementation-plan.md`, `infra/README.md`, `infra/.gitignore`,
   drop `infra/kafka/`.
3. Accept/adjust the scope additions (2.6) and the R7/R11 carve-outs (3.1/3.2) explicitly, ideally as
   `AGENTS.md`/plan decision-log lines so they're not re-flagged later.
4. Tidy nits in §6 when convenient.

*No files were modified to produce this review.*
