# Admin SPA

A tiny, dependency-free admin console (plain HTML/CSS/JS) for the control plane. It is
served **statically by nginx** at extensionless, tenant-prefixed routes and talks to the
versioned control-plane API on the same origin (nginx proxies `/{tenant}/v1/...` to the
Spring app). See `infra/nginx/conf.d/default.conf`.

## Routes (via nginx on port 80)

| URL | Page | Backend it calls |
|---|---|---|
| `http://localhost/{tenant}/login` | Google SSO sign-in | `GET /{tenant}/v1/auth/login` → 302 to Google |
| `http://localhost/{tenant}/experiments` | list / create / inspect / delete experiments | `/{tenant}/v1/experiments` (CRUD) |
| `http://localhost/{tenant}/analytics` | per-experiment results | `/{tenant}/v1/experiments/{id}/results` |

The tenant is the first path segment; each page reads it from `window.location` (see
`assets/api.js`). Files here are mounted read-only into the nginx container at
`/usr/share/nginx/html`.

For local convenience, nginx redirects the bare/tenant-less entry points to the default
tenant `demo` (`/` and `/login` → `/demo/login`, etc.), and the login page forwards an
already-signed-in user to `/{tenant}/experiments`. So `http://localhost` lands on the
demo login, and a valid session skips straight to the experiments list.

## Auth flow

1. `login` → **Sign in with Google** navigates to `/{tenant}/v1/auth/login`; the backend
   redirects to Google and, on success, sets an httpOnly `session` cookie and redirects
   to `/{tenant}/experiments`.
2. Every API call rides that cookie (same origin). A `401` sends the user back to
   `/{tenant}/login`.

> Google Cloud Console needs the redirect URI `http://localhost/login/oauth2/code/google`
> registered for the OAuth client.

## Files

- `login.html` / `assets/login.js` — sign-in, "already signed in" shortcut.
- `experiments.html` / `assets/experiments.js` — list (paged), a shared create/**edit**
  form (variant editor with live allocation total; Edit prefills it and issues a `PUT`),
  expand-to-view variants, delete.
- `analytics.html` / `assets/analytics.js` — experiment selector + results table.
- `assets/api.js` — shared client (tenant detection, fetch wrapper, 401 → login).
- `assets/styles.css` — shared styles.

Browser end-to-end tests live in `../e2e`.
