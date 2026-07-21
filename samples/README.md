# samples

Copy-paste examples of integrating with the **public data plane** (the anonymous,
browser-facing API). These are not part of the deployable — they show what a real
client sends.

## `visitor-client.html`

A tiny, dependency-free widget that exercises both data-plane endpoints:

| Button | Call | Contract |
|---|---|---|
| Assign | `GET /{tenant}/v1/assign?experiments=1,2` | → `{ assignments: { "<expId>": { id, content } }, anon_visitor_id, degraded }` |
| Track exposed / converted | `POST /{tenant}/v1/track` `{ status, data: { <expId>: <variantId> } }` | → `{ accepted }` |

Identity is header-based (no auth, no cookies sent to the API):

- A first-party `vf_vid` (random UUID) is generated on the embedding page and sent as
  `X-Visitor-Id`.
- The server's `anon_visitor_id` is cached as `vf_aid` and sent back as `X-Anon-Id`, so
  repeat calls stick to the same bucket. `track` reuses the variant ids returned by the
  last `assign`.

### Configure

Edit the three constants at the top of the `<script>`:

```js
const API = "https://YOUR-HTTPS-HOST", TENANT = "your-tenant", EXPERIMENTS = [1, 2];
```

For the **local stack** (nginx on port 80): `API = "http://localhost"`,
`TENANT = "demo"`, `EXPERIMENTS = [1]`.

### Cross-origin

The data plane sends permissive CORS for `/*/v1/assign` and `/*/v1/track`
(`Access-Control-Allow-Origin: *`, `GET,POST,OPTIONS`, credentials off — identity is
header-based), so the widget works embedded on any origin. The control plane (admin)
is **not** CORS-open; it is cookie-authenticated and same-origin only.

### Try it

Open the file from any origin and click **Assign**, then **Track exposed** /
**Track converted** — responses are logged newest-first in the black panel. Verified
against the running local stack:

```
ASSIGN 200  { "anon_visitor_id": "59", "assignments": { "1": { "content": "...", "id": "2" } }, "degraded": true }
TRACK  200  { "accepted": "1" }
```

(`degraded: true` above just means one requested experiment id didn't exist — the
others still resolve.)
