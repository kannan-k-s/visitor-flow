# Admin SPA — browser end-to-end tests

Real-browser Playwright tests for the admin SPA. They drive Chromium against the
**nginx origin on port 80** (`http://localhost`, no port) — the same entry point an
operator uses — which serves the static SPA and reverse-proxies the versioned API to
the Spring app on `8080`. Every assertion is triggered by a real user action
(navigation, click, form submit) that issues a real network request; nothing is mocked
and no events are synthesised.

## What is covered

| Case | Real action | Verifies |
|---|---|---|
| Google SSO start | click **Sign in with Google** | backend builds a correct OAuth authorize request (signed `state`, portless `redirect_uri`) and the browser navigates to `accounts.google.com` |
| Auth guard (experiments) | visit `/demo/experiments` with no session | client gets `401` and redirects to `/demo/login` |
| Auth guard (analytics) | visit `/demo/analytics` with no session | same redirect |
| Create experiment | fill + submit the create form | `POST /demo/v1/experiments`, new row appears |
| Edit experiment | click **Edit**, change split, save | `PUT /demo/v1/experiments/{id}`, changes persist |
| Inspect variants | click **Variants** | `GET /demo/v1/experiments/{id}` renders both variants |
| Analytics results | open `/demo/analytics?experiment={id}` | `GET …/results` renders per-variant table + orphan count |
| List → analytics link | click a row's **Analytics** link | navigates and loads that experiment's results |
| Delete experiment | click **Delete**, accept the dialog | `DELETE …/{id}`, row disappears |

Google SSO cannot be completed by a bot, so the authenticated tests mint the exact
session cookie the OAuth success handler would issue — an HS256 JWT signed with the
app's `JWT_SIGNING_SECRET`, carrying the seeded user's id/tenant/email read from the
real MySQL. This mirrors how the existing Java `ExperimentApiE2ETest` authenticates.

## Prerequisites

1. Infra stack up: `docker compose --env-file .env -f infra/docker-compose.yml up -d`
   (nginx, mysql, redis, rabbitmq). nginx serves the SPA from `../ui`.
2. The Spring app running on `8080` with the same `.env` loaded:
   `java -jar web/target/web-0.0.1-SNAPSHOT.jar` (after `mvnw.cmd -pl web -am package -DskipTests`).
3. Repo-root `.env` present (provides `JWT_SIGNING_SECRET` and `MYSQL_PASSWORD`).

## Run

```bash
cd e2e
npm install
npm run install:browsers   # one-time: downloads Chromium
npm test                   # headless
npm run test:headed        # watch it in a real window
npm run report             # open the HTML report
```

Override the origin with `E2E_BASE_URL` (defaults to `http://localhost`).
