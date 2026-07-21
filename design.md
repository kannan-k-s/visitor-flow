# Variant — Experimentation & Assignment Service

**Design Document**

A multi-tenant service answering one question on every page load —
**"Which variant should this visitor see right now?"** — then recording exposures
and conversions and reporting the winner. It sits on the **critical render path**
(every visit to every customer site calls it), so **latency and fail-safety drive
every decision**.

---

## 1. Goals

- Low-latency assignment: experiments **split by strategy**, each under a flat deadline (**hash 100 ms · SWRR 200 ms**) → on timeout, return the **default variant** for that group.
- Two per-experiment strategies — **Hash** (default) and **SWRR** (opt-in) — behind one interface.
- **Durable, deduplicated three-status tracking** (`assigned`/`exposed`/`converted`) via RabbitMQ; eventual consistency acceptable.
- **Multi-tenant** control plane (**Google OAuth + RBAC**); **fully anonymous** data plane.
- **LLM-generated** variant content, off the hot path.
- Abuse protection via **visitor-id / IP rate limiting**.

---

## 2. Architecture

Two planes, opposite requirements. Built as a **multi-module Maven project**:

```
web ─► admin  ─► data
   └─► visitor ─► data
```

- **`data`** — shared persistence + domain: entities, repositories, and MySQL/Redis access. Both planes depend on it.
- **`visitor`** — data-plane logic **and its own controllers** (`/assign`, `/track`): assignment (Hash/SWRR), identity resolution, tracking emit, rate limiting, and its filters. Depends on `data`.
- **`admin`** — control-plane logic **and its own controllers**: experiment config, results, RBAC, and its filters. Depends on `data`.
- **`web`** — the runnable app: holds `DemoApplication`, the **single filter-hook** that registers/orders the filters from both `admin` and `visitor`, and **authentication** (OAuth login + JWT). Depends on `admin` + `visitor`.

Assembled into **one deployable now**; the module split follows the plane boundary, so the microservice split (§15) is cheap — `visitor+data` and `admin+data` become two Boot apps.

- **Data plane** (public, anonymous, hot): `/assign`, `/track`. Massive reads, latency-critical, fail-safe.
- **Control plane** (private, human): config console + dashboard. OAuth + RBAC.

```
 Customer <script> ──GET /{tenant}/v1/assign──►  Rate limiter (Bucket4j+Redis, fail-OPEN)
   (client owns cookies)                            │
                                                    ▼
                  Assignment Svc ─ emits `assigned` ─► RabbitMQ ─► consumer ─► MySQL
                  (fire-and-forget)
                  timeout→default      (partition by visitor_id)   (events+aggregates)
                  Hash | SWRR ▲                    ▲
                   reads cfg  │ Redis config cache  │ POST /track {status, data:{exp→var}} (client)
   Control (OAuth+RBAC) ── Console ─┘   Dashboard ──┘ (reads MySQL directly)
```

| Decision | Reasoning |
|---|---|
| Split data / control planes | Opposite needs: hot+anonymous+fail-safe vs warm+authenticated+consistent. |
| Assignment (near-)pure: hash over cached config | Only way to hold the latency ceiling and stay fail-safe on the render path. |
| Redis = cache, MySQL = source of truth | A cache must be rebuildable from the source of truth. |
| LLM at config time, not assign time | LLM is slow/costly/flaky — incompatible with the render path. |

---

## 3. Domain Model & Buckets

- **Tenant** — customer org; isolation boundary; owns users, experiments, data.
- **User** — console human; belongs to a tenant; has a role.
- **Visitor** — end-user of a customer site; identified by a visitor id (§6.1).
- **Experiment** — one test; has variants, an allocation, a strategy; one variant is the **default** (baseline + safe fallback).
- **Variant** — one version shown. **Allocation** — % per variant, summing to 100 (one variant is the default baseline).
- **Assigned** — variant computed for a visitor (server-emitted). **Exposure** — visitor was *shown* the variant (client-emitted). **Conversion** — visitor reached the experiment's goal (client-emitted, `status:converted`); conversion rate = converted / exposed.

**Buckets.** 10,000 slots (`0–9999`) = 100%; each = 0.01%. Variants own contiguous ranges; the range containing a visitor's bucket decides the variant. *How* placement happens is the strategy (§6).

```
50/50 → default 0–4999, variant 5000–9999
90/10 → default 0–8999, variant 9000–9999
```

---

## 4. Multi-Tenancy & Tenant Identification

- **Shared schema**, mandatory `tenant_id` on every tenant-scoped row; repository layer refuses unscoped queries. (DB-per-tenant is a future option.)
- A **once-per-request filter** resolves the tenant from the URL path `/{tenantName}/v1/...` on **both planes**, mapping `tenantName → tenant_id` into immutable request context (never from the body). On the data plane the same filter also resolves the canonical `anon_visitor_id` (§6.1) into context.

---

## 5. Authentication & Authorization

- **Control plane:** Google SSO via Spring Security `oauth2-client` (OIDC Authorization Code) — declarative, **no manual access/refresh-token handling**. After login, a **stateless JWT (30-min expiry)** in an httpOnly cookie carries auth; **backend-revocable / refreshable sessions are next** (§15). No passwords. Tenant membership is invite-based.
- **RBAC:** roles **Admin** and **Viewer**. Privileges granted **two ways** — via role, or **directly to a user**. (Per-resource scoping is later.)
- **Data plane:** **no auth — fully anonymous.** Protected by rate limiting and (later) referrer validation.

---

## 6. Assignment Engine (core)

One interface, two strategies, shared identity + fallback:

```
interface AssignmentStrategy { assign(identity, experiment) -> variant }
```

### 6.1 Identity — two visitor ids

**The data-plane server never reads or writes cookies** — the loop is client-owned: server returns the resolved `anon_visitor_id` in the **response body** → client stores it as a **first-party cookie** (`document.cookie`) → client replays it as a **header** on later requests. All cookie handling stays first-party (no third-party-cookie problems).

Filter inputs arrive as **path + headers, never the body** (so the filter needs no stream buffering):

| input | where | meaning |
|---|---|---|
| tenant | URL path `/{tenant}/v1/...` | required |
| `experiments` | query param | required; read by controller |
| `X-Anon-Id` | header | anon id replayed from the first-party cookie |
| `X-Visitor-Id` | header | logged-in user id — only after login |

**Every visitor always has a canonical `anon_visitor_id` (one per browser). A `visitor_id` (login identity) is tied to *one or more* anons — one per device — stitched *many-to-one* in `identity_links`; among them one is the *canonical anon* (Redis `link:{visitor_id}→anon`) **get-or-created** (first-writer `SETNX`) *only* on the **anon-id-less `visitor_id` path** (step 2). `/assign` accepts either id.** The **once-per-request filter** (same layer as tenant resolution) resolves the anon into request context — assignment never resolves ids itself. Order:

1. `X-Anon-Id` present → use it.
2. else `X-Visitor-Id` present → use the anon **linked** to it (`visitor_id→anon`, Redis-cached); generate + link if none.
3. else → generate a new anon (server-side; the no-id concurrent-first-call race is §6.8).

Hashing always uses this context anon, so the variant is **stable and never flips** across anonymous / first-login / later-login; a new `visitor_id` is linked once (never replaces the anon). Consequence: a caller sending only `visitor_id` (anon-id-less/server-to-server, or a logged-in first hit) stays consistent via the reused link — including the **same user across devices**. Stickiness = the identity; improve the *identifier*, not storage.

### 6.2 Hash strategy (default)

```
bucket  = murmur3(anon_visitor_id + ":" + experiment_id) % 10000
variant = range_lookup(allocation, bucket)
```

- **Per-experiment salt** (`experiment_id`) decorrelates experiments.
- **Avalanche hash** (MurmurHash3 / xxHash) defeats structured-id skew — *not* a language `hashCode` (unstable), *not* raw `id % N` (the real cause of "everyone in one variant", not small samples).
- **Sticky, deterministic, zero storage**; survives restarts; uniform → allocation honoured across the population.
- **Small-N honesty:** even *in expectation*, not exactly (~48/52 at N=100). Same as nginx `split_clients`, and for the same reason: assignment must be sticky + stateless.

### 6.3 SWRR strategy (opt-in — exact allocation)

For low-traffic experiments needing **exact** allocation from visitor 1: Smooth Weighted Round-Robin (nginx's weighted-upstream algorithm).

```
each new assignment:  current_weight[v] += weight[v];  chosen = argmax;  current_weight[chosen] -= total
weights A=5,B=1 → A A A B A A  (exactly 5:1, interleaved, from visitor 1)
```

- Decides by **arrival order** → **not reproducible** → must **persist `identity→variant`** and needs a **shared atomic counter**.
- **Atomicity:** the full read-modify-write (add weights → argmax → subtract → return) runs as a **single Redis Lua script** — race-free across nodes.
- Costs a hot counter key + per-visitor storage; falls back to default on infra failure — hence **opt-in**. It exists for a real need: **exact allocation from visitor 1** on a low-traffic experiment where a stakeholder wants a precise split honoured immediately. Trading statelessness for that exactness is worth it only when it's a hard requirement — at scale, hash's expectation-level accuracy already holds (§14) — so hash stays the default.

### 6.4 Strategy = per-experiment config

```jsonc
{ "assignment_strategy": "hash",   // "hash" (default) | "swrr"
  "sticky_bucketing": false, "default_variant": "a",
  "allocation": { "a": 50, "b": 50 } }
```

Default **hash**; cost isolated (hash touches no counter/store). Single selector, run under the per-strategy deadline (§6.6):

```
resolveVariant(req, exp):
   identity = context.anon           # already resolved in filter (§6.1)
   if exp.sticky_bucketing and (v = stickyStore.get(identity, exp)): return v
   try:
       v = (exp.strategy == "swrr") ? swrr.assign(...) : hash.assign(...)
       if exp.sticky_bucketing: stickyStore.put(identity, exp, v)   # async
       return v
   on timeout/error: return exp.default_variant
```

### 6.5 Sticky bucketing (opt-in)

Persist the last-served variant → survives config changes; **only new visitors feel a change**. Off by default (re-adds per-visitor storage). Layered lookup: client cookie → Redis → MySQL. Caveat: changing live allocation still **mixes populations** — stickiness-safe, not analysis-safe.

### 6.6 Timeouts & fail-to-default

**Split the requested experiments by strategy and run each group under its own flat deadline** — no budget formula, no per-experiment accounting:

```
1. batch-load config for all requested experiments (one Redis read)
2. split → hashExps, swrrExps
3. run hashExps under 100 ms   → on timeout/error: default ALL hashExps
4. run swrrExps under 200 ms   → on timeout/error: default ALL swrrExps
5. merge + return
```

- **Two deadlines, group-level fallback.** Hash is the batched config read + a microsecond CPU hash — **no per-experiment round-trips** — so 100 ms is a safety net it never approaches. SWRR does one Redis (Lua RMW) call per experiment, so it gets the looser 200 ms. If a group blows its deadline or a call errors, **that whole group falls back to `default_variant`** — no partial bookkeeping. Hash runs first and is effectively instant, so the request's real ceiling is the SWRR deadline (~200 ms). If the config read itself fails, **all** experiments default.
- **Enforced on the request thread:** each Redis/DB call carries a small socket timeout and throws inline; between SWRR calls the handler checks elapsed vs the deadline. No `Future`, thread pool, watchdog, or timer.
- Defaulted experiments are tagged `assigned.degraded` and excluded from results (§8). Fallback chain: **client-cookie variant → strategy → default**.

Both deadlines are **application properties** (`assign.hash-deadline-ms`, `assign.swrr-deadline-ms`); ~15–20 ms is the expected latency with healthy Redis — the deadlines are ceilings, not targets. SWRR calls are **sequential now**; **pipelining is a later optimization** to flatten the p99 tail (§13).

### 6.7 Referrer validation (planned)

Validate request referrer/origin against an experiment-level **`allowed_domains`** list before assignment. Config field + enforcement are a later iteration.

### 6.8 Concurrency (visitor side)

- **Anon get-or-create (`visitor_id` present):** concurrent first requests must not mint different anons. The `visitor_id → anon` link is created atomically via **`SETNX link:{tenant}:visitor:{id}`** — first writer wins, others read it; backed by a unique constraint on `identity_links`.
- **Brand-new visitor, no ids, concurrent first calls:** with no shared identifier (no anon, no `visitor_id`) the server genuinely can't tell these requests apart — each mints a fresh anon and the client keeps whichever cookie write lands last. **This can't be eliminated** — client-generated UUIDs don't help (multiple tabs/browsers race the same way), and there's no server key to coalesce on. **Accepted as rare and self-correcting:** a few orphan exposures at first contact, after which every subsequent request shares the one surviving cookie and converges — no effect on stickiness thereafter.
- **SWRR first-assign race:** two concurrent first assigns each consume a counter tick and may differ. The sticky write is **put-if-absent** (`SETNX sticky:{visitor}:{exp}`); the loser discards its result and returns the stored one. The extra tick is a negligible allocation perturbation. (The counter RMW itself is atomic via the Lua script, §6.3.)
- **Duplicate / concurrent exposures:** deduped by the unique event key; the consumer upserts (§7).
- **Rate limiting:** atomic token consumption in Redis (Bucket4j), correct across nodes.
- **Config read during a live update:** config is a single Redis key → atomic read (no torn config); a cold-cache reload uses **single-flight** so a herd doesn't stampede MySQL.
- **Multiple anons per `visitor_id` (multi-device):** the `visitor_id → anon` link has two roles — a **1:1 canonical** (Redis `SETNX`, used *only* for step-2 resolution when no anon is sent) and a **many-anons → one `visitor_id`** record in `identity_links` (for reporting stitch). A device that already has its own anon keeps it (step 1, no flip), so a user who browsed anonymously on several devices ends up with a device-local anon per browser, all stitched to them. Each browser stays consistent; cross-device merge of assignments is deferred (§15).

---

## 7. Tracking — three-event funnel (RabbitMQ)

Assignment produces a **funnel of three per-visitor statuses**, split by *who is certain of what*:

| event | emitted by | when | means |
|---|---|---|---|
| `assigned`  | **server**, during `/assign`        | variant computed        | this visitor *would* see variant X |
| `exposed`   | **client**, `POST /track {status:"exposed"}`   | variant actually rendered/applied | this visitor *did* see variant X |
| `converted` | **client**, `POST /track {status:"converted"}` | reached the experiment's goal | this visitor converted |

**Why split `assigned` from `exposed`.** `/assign` also fires for prefetch, bots, batched experiments the page never renders, and DOM changes that throw before paint — none of which are real views. Only the client knows a variant was *shown*, so it owns `exposed`. The **conversion-rate denominator is `exposed`, never `assigned`** — that is what keeps the rate trustworthy. `assigned` is retained as a QA/telemetry signal, not a denominator (§8, §14).

- All three are **partitioned by numeric `anon_visitor_id`** → per-anonymous-visitor ordering;
  **eventual consistency is fine** (dedup makes arrival order irrelevant to counts). RabbitMQ does not
  provide native partitions, so Spring Cloud Stream creates one direct `tracking-events` exchange and
  four queues. Routing keys `tracking-events-0` through `tracking-events-3` each bind to one queue;
  the producer hashes `anonVisitorId` to select exactly one of them.
- **Status-batched; client reports the variant.** One endpoint — `POST /{tenant}/v1/track` with body `{ status: "exposed"|"converted", data: { exp→variant } }` — records that status for the visitor on each listed experiment, with the client **echoing the variant(s)** it received from `/assign` and rendered (only the client knows what was actually shown; stays correct even if config changed after assign). Only `exposed`/`converted` are accepted — `assigned` is server-only. The server records verbatim — it does **not** re-resolve or call the assignment engine (no SWRR tick, no sticky read). Identity is resolved from headers **lookup-only** (no anon get-or-create); a missing identity yields an orphan event. **Trade-off:** the reported variant is spoofable — cross-checked against `assigned` (§14).
- **`assigned` is emitted best-effort** on the hot path; a RabbitMQ send failure is logged and swallowed,
  so tracking never fails a render (§6.6). Client `exposed`/`converted` calls are separate and use the
  durable binding serially; broker failure returns `503` so the client can retry.
- A **`consumer`** dedups and writes MySQL (`events` + aggregates).
- **Idempotency:** unique key `(tenant, experiment, visitor, status)`; `visitor` is the **context anon** (per-device, §6.1), so multiple devices are already distinct rows. One anon maps to **one variant per experiment** — an invariant, not a coincidence (deterministic hash; SWRR/sticky persist) — so `variant` is deliberately *not* in the key. The only way it would break is a live allocation change, which §14 forbids and the assigned-vs-reported variant check (§14) would flag rather than silently double-count. Consumer upserts, so a duplicate `exposed`/`converted` never double-counts (→ unique visitors; repeat conversions collapse to one).
- A `converted` with **no matching `exposed`** is a data-quality signal (converted but never exposed) — counted separately, not in the rate. No attribution window yet — a late conversion still counts (§15).

---

## 8. Results Dashboard

Per experiment, per variant: **assigned, exposed, converted, and conversion rate = converted / exposed**.

- **Reads MySQL aggregates directly — no Redis on the control plane.**
- Two diagnostics from the split funnel: **exposure rate `exposed/assigned`** (a per-variant gap flags a variant-specific render/JS failure) and an **SRM check** — realized `assigned` split vs configured allocation (§14).
- Degraded assignments (fail-to-default, §6.6) are **excluded** via the `assigned.degraded` flag.
- Raw rates for now; **significance reporting (confidence intervals + verdict) is deferred** (§15).

---

## 9. Config Console

- OAuth + RBAC. Experiment CRUD: variants (+ content), allocation, strategy, `sticky_bucketing`, `default_variant`, goal, `allowed_domains` (planned).
- **Experiments are always active** (no lifecycle for now).
- Validation: allocation sums to 100; exactly one default variant; **max 20 experiments per `/assign` call**.

---

## 10. LLM-Assisted Element

- LLM generates variant content (headline/CTA) **at config time, async, cached, human-reviewed — never on `/assign`**.
- **Generate once, serve to millions** — cost amortised, not per-visit.
- Generation failure/pending → variant uses human-authored default content. **LLM key server-side only.**

---

## 11. Storage

- **MySQL = source of truth:** tenants, users, experiments, events + aggregates, sticky assignments. Snapshots + binlog for DR.
- **Redis = cache / ephemeral:** config cache, SWRR counters (AOF-persisted), rate-limit buckets — all rebuildable or operational.
- **RabbitMQ = durable event pipe** (tracking → four partition queues → MySQL).
- **Config cache in Redis, never in-process memory** — all nodes read one consistent view (in-memory caches diverge across a cluster). MySQL is the config source of truth; console writes update MySQL + refresh Redis; `/assign` reads Redis (on miss/failure it fails to default).
- Redis counters are reconstructable from MySQL events; the dashboard reads MySQL.

---

## 12. Rate Limiting

- **Key by `visitor_id` when present, else client IP**; plus a coarse **per-tenant** ceiling.
- **Token bucket via Bucket4j, Redis-backed** (global across nodes). *(Token bucket fits rate limiting — it rejects excess — unlike allocation, which must never reject.)*
- **IP caveat:** NAT/shared IPs → generous IP limits; prefer visitor-id keying once a cookie exists.
- **Fails OPEN** (never breaks a page); over-limit `/assign` still returns the default.

---

## 13. Scale

- **Assignment (reads):** massive, cheap — hash over Redis-cached config; stateless nodes scale **horizontally + linearly**.
- **Tracking (writes):** three streams by volume — `assigned` (every `/assign`, highest) ≥ `exposed` (only renders) ≥ `converted` (only conversions); all flow through RabbitMQ's four partition queues and are deduped by the consumer to unique-visitor rows in MySQL. Write scaling is decoupled from reads. `assigned` is the hot stream — **sampling or an aggregate-only counter** is a later lever if its volume dominates.
- **Results / config:** low-volume / read-mostly.
- **Multi-experiment assign:** hash experiments are CPU-only (free); SWRR/sticky each cost a Redis round-trip — **sequential now** (cap 20 ≈ 15–20 ms with healthy Redis), **pipeline later** to flatten the p99 tail and raise the cap.
- **Watch:** SWRR counter hot key (opt-in only); sticky-store growth; tracking throughput (partition `events` by tenant/time).

---

## 14. Correctness & Statistical Validity

- **Assignment:** reproducible (hash) or persisted (SWRR/sticky). Validated by hashing 1e6 synthetic ids and asserting the split matches allocation within ~0.1%.
- **Counting:** idempotent, durable, rebuildable. Conversion rate uses **`exposed`** (actual views) as the denominator, not `assigned`.
- **SRM (sample-ratio mismatch):** the `assigned` stream lets us test the realized split against configured allocation (chi-square); a mismatch flags a bucketing or instrumentation bug **before** any result is trusted.
- **Client-reported variants (§7):** each `exposed`/`converted` carries the variant the client rendered; the consumer cross-checks it against the server's `assigned` variant for that visitor — a mismatch flags a client bug or tampering. For hash experiments the server can recompute the expected variant on ingest and flag/drop mismatches (optional hardening).
- **Statistics (deferred, §15):** low traffic is **noisy, not biased** — no algorithm manufactures power the traffic lacks, so significance reporting (CI + verdict, frequentist/Bayesian) is later. Applied now: a **balanced split** and **fewer variants** maximise power; **don't reallocate live** (mixes populations).

---

## 15. Trade-offs & Next Steps

**Trade-offs accepted:** hash default (statelessness/scale over exact small-N); shared-schema tenancy (simplicity over physical isolation); Redis-as-cache-only; sticky bucketing off by default; LLM at config time.

**Next:** conversion **attribution window**; **conversion value / revenue** tracking; significance reporting (CI + verdict); backend-revocable / refreshable auth sessions; split data/control planes into microservices; global (cross-experiment) holdback — an uncounted "default without exposure" group for aggregate program lift; multi-armed/contextual bandits; cross-site learning; distributed-exact SWRR; streaming analytics + sequential testing; per-experiment **identity basis** (anon vs `visitor_id`) — trade cross-device consistency against login-time flips; targeting/segmentation; per-resource privilege scoping; referrer/allowed-domains enforcement; DB-per-tenant.

---

## Appendix A: API

**Data plane** (anonymous, tenant in path, rate-limited):
```
GET  /{tenant}/v1/assign?experiments=exp1,exp2  (≤20)   headers: X-Anon-Id? · X-Visitor-Id?
     → { assignments: { exp1: "a", exp2: "b" }, anon_visitor_id, degraded }   // emits `assigned` server-side
POST /{tenant}/v1/track   headers: X-Anon-Id? · X-Visitor-Id?
     body: { status: "exposed"|"converted", data: { exp1: "a", exp2: "b" } }
     // one status, batched over experiments; client echoes the shown variant; `assigned` is server-only
```
**Control plane** (OAuth session + RBAC):
```
POST/GET/PATCH /{tenant}/v1/experiments[/:id]
GET  /{tenant}/v1/experiments/:id/results
POST /{tenant}/v1/experiments/:id/generate        (async LLM content)
POST /{tenant}/v1/users/invite
```

## Appendix B: Data Model

```sql
tenants(id, name, created_at)
users(id, tenant_id, email, google_sub, role, created_at)              -- role: admin|viewer
user_privileges(user_id, privilege)                                    -- direct grants, added to role
experiments(id, tenant_id, name, strategy, sticky_bucketing,
            default_variant_id, allowed_domains, created_at)
variants(id, experiment_id, key, is_default, content, alloc_pct)       -- sum(alloc_pct)=100
assignments(tenant_id, experiment_id, visitor_id, variant_key, assigned_at,
            PRIMARY KEY(tenant_id, experiment_id, visitor_id))          -- only when sticky_bucketing
identity_links(anon_visitor_id PK, tenant_id, visitor_id)              -- stitch: many anons → one visitor_id
                                                                       -- (Redis link:{visitor_id}→anon: canonical anon, get-or-created on the anon-id-less path)
events(event_id PK, tenant_id, experiment_id, variant_key, visitor_id, status, ts, degraded,   -- visitor_id = context anon (per-device); join identity_links → person
       UNIQUE KEY dedup(tenant_id, experiment_id, visitor_id, status))    -- status: assigned|exposed|converted; one anon → one variant/exp (invariant)
```

Redis keys (rebuildable / ephemeral): `cfg:{tenant}:{exp}` · `link:{tenant}:visitor:{id}→anon` · `swrr:{tenant}:{exp}` (AOF) · `rl:{visitor|ip|tenant}:{id}`.

## Appendix C: Decision Log

| # | Decision | Why |
|---|---|---|
| D-1 | 10,000 buckets as the fixed 100% | Integer math, 0.01% precision, inspectable. |
| D-2 | `murmur3(anon_visitor_id + ":" + exp_id)` | Avalanche defeats structured-id skew; salt decorrelates experiments. |
| D-3 | Hash default, SWRR opt-in | Hash is stateless/fail-safe/scalable → the default. SWRR buys exact allocation from visitor 1 for low-traffic experiments that need it, at the cost of state — opt-in so that cost is paid only when exactness is a hard requirement. |
| D-4 | Two ids; server sets no cookie | Client-owned first-party cookie avoids 3rd-party issues; login id linked as alias, no flip. |
| D-5 | SWRR counter via single Redis Lua script | Atomic RMW, race-free across nodes. |
| D-6 | Fail-to-**default variant** on timeout | Known-safe baseline; never an untested variant during an outage. |
| D-7 | Config cache in Redis, not in-memory | One consistent view across nodes. |
| D-8 | Tracking via one RabbitMQ direct exchange and four queues, partitioned by anon visitor id | Per-visitor ordering without native broker partitions; eventual consistency; decouples writes. |
| D-9 | Bucket4j token bucket, fail-open | Rate limiting should reject excess; must never break a page. |
| D-10 | LLM at config time, cached, human-reviewed | Slow/costly/flaky — off the hot path; generate once, serve millions. |
| D-11 | Atomic anon get-or-create via `SETNX` (visitor_id present) | One canonical anon under concurrency; the no-shared-id first-call race is accepted as rare/self-correcting. |
| D-12 | Split by strategy; flat per-group deadline (hash 100 ms · SWRR 200 ms); on timeout default the whole group | Hash is CPU-only so its deadline never fires; SWRR gets the looser bound; group-level fallback avoids per-experiment bookkeeping (trade: one slow SWRR call defaults its group — per-experiment fallback is a later refinement); pipeline later. |
| D-13 | Funnel: `assigned` (server) / `exposed` + `converted` (client); rate = converted/exposed | Only the client knows a variant was *shown*; assign-time counts prefetch/bots/unrendered. Keeps the denominator honest; `assigned` retained for SRM + exposure-funnel QA. |
| D-14 | Hash on the **context anon** → **per-browser** stickiness, **no login-time flip** | The variant never changes when a visitor logs in / re-logs; each browser stays stable. Trade-off: logged-in users are *not* consistent across devices (cross-device merge deferred, §15). Hashing on `visitor_id` would give cross-device consistency but reintroduce the flip — so the identity basis becomes a **future per-experiment config** (§15). |
| D-15 | SWRR Redis calls **sequential now; pipeline/batch later** | At ≤20 experiments the sequential round-trips fit the 200 ms deadline; **pipelining (or one multi-key Lua batch)** is a later optimization to flatten the p99 tail and raise the experiment cap (§6.6, §13). |
| D-16 | Single `POST /track` `{status, data:{exp→variant}}` (`exposed`\|`converted`); client echoes the shown variant | One status batched over experiments; dedup key `(tenant,exp,visitor,status)` (visitor = per-device anon; one anon → one variant/exp by invariant); conversion = `status:converted` against the experiment's configured goal (§9). No server re-resolution (simpler, correct even if config changed post-assign). Trade: spoofable → cross-check vs `assigned` (§14). |
