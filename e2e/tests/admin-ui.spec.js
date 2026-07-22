// Browser end-to-end tests for the admin SPA.
//
// These drive a real Chromium browser against the real nginx origin (port 80),
// which serves the static SPA and proxies the versioned API to the Spring app on
// 8080. Every assertion is triggered by a real user action (navigation, click,
// form submit) that produces a real network request — nothing is mocked or
// synthetically dispatched.
const { test, expect } = require('@playwright/test');
const { randomUUID } = require('crypto');
const { sessionCookie } = require('../support/session');

const TENANT = 'demo';
const ADMIN_EMAIL = 'admin@example.com';

test.describe('Admin SPA — unauthenticated', () => {
  test('login page starts real Google SSO when the button is clicked', async ({ page }) => {
    await page.goto(`/${TENANT}/login`);

    await expect(page.getByRole('heading', { name: 'Experiment Admin' })).toBeVisible();
    await expect(page.locator('[data-tenant]')).toHaveText(TENANT);

    // A real click navigates to /{tenant}/v1/auth/login, which the backend turns into
    // a 302 to Google's authorization endpoint; the browser really issues that request.
    // Capturing the outgoing request proves the backend built a correct authorize URL
    // (signed state + portless redirect_uri) independent of the Google console config —
    // Google itself only accepts the redirect_uri once it is registered there.
    const [authRequest] = await Promise.all([
      page.waitForRequest((req) => req.url().startsWith('https://accounts.google.com/o/oauth2/'),
        { timeout: 15000 }),
      page.getByRole('button', { name: /sign in with google/i }).click()
    ]);

    const authUrl = new URL(authRequest.url());
    expect(authUrl.searchParams.get('client_id')).toBeTruthy();
    expect(authUrl.searchParams.get('state')).toBeTruthy(); // signed tenant state
    expect(authUrl.searchParams.get('redirect_uri'))
      .toBe('http://localhost/login/oauth2/code/google');
    expect((authUrl.searchParams.get('scope') || '')).toContain('email');

    // And the browser really lands on Google's domain.
    await page.waitForURL(/accounts\.google\.com/, { timeout: 15000 });
  });

  test('bare root and tenant-less paths redirect to the tenant login', async ({ page }) => {
    // nginx forwards the tenant-less entry points to the default local tenant; with no
    // session the login page then stays put.
    await page.goto('/');
    await page.waitForURL(new RegExp(`/${TENANT}/login$`), { timeout: 15000 });
    await expect(page.getByRole('button', { name: /sign in with google/i })).toBeVisible();

    await page.goto('/login');
    await page.waitForURL(new RegExp(`/${TENANT}/login$`), { timeout: 15000 });
  });

  test('experiments page bounces to login without a session', async ({ page }) => {
    await page.goto(`/${TENANT}/experiments`);

    // The page loads statically, its API call returns 401, and the client redirects.
    await page.waitForURL(new RegExp(`/${TENANT}/login$`), { timeout: 15000 });
    await expect(page.getByRole('button', { name: /sign in with google/i })).toBeVisible();
  });

  test('analytics page bounces to login without a session', async ({ page }) => {
    await page.goto(`/${TENANT}/analytics`);
    await page.waitForURL(new RegExp(`/${TENANT}/login$`), { timeout: 15000 });
  });
});

test.describe('Admin SPA — authenticated', () => {
  test.describe.configure({ mode: 'serial' });

  const experimentName = `e2e-${randomUUID().slice(0, 8)}`;
  let cookie;
  let experimentId;

  test.beforeAll(async ({ baseURL }) => {
    cookie = await sessionCookie(baseURL, TENANT, ADMIN_EMAIL);
  });

  test.beforeEach(async ({ context }) => {
    await context.addCookies([cookie]);
  });

  test('an authenticated visit to the login page forwards to experiments', async ({ page }) => {
    // login.js probes the session cookie and, when valid, replaces the URL — a real
    // client-side redirect, not a synthetic one.
    await page.goto(`/${TENANT}/login`);
    await page.waitForURL(new RegExp(`/${TENANT}/experiments$`), { timeout: 15000 });
    await expect(page.getByRole('heading', { name: 'Experiments', exact: true })).toBeVisible();
  });

  test('an authenticated visit to bare root ends on experiments', async ({ page }) => {
    // / -> (nginx) /demo/login -> (cookie-aware) /demo/experiments
    await page.goto('/');
    await page.waitForURL(new RegExp(`/${TENANT}/experiments$`), { timeout: 15000 });
    await expect(page.getByRole('heading', { name: 'Experiments', exact: true })).toBeVisible();
  });

  test('creates an experiment through the real form', async ({ page }) => {
    await page.goto(`/${TENANT}/experiments`);
    await expect(page.getByRole('heading', { name: 'Experiments', exact: true })).toBeVisible();

    // The seeded 50/50 rows should read as a valid 100% total.
    await expect(page.locator('[data-alloc-total]')).toContainText('100%');

    await page.fill('#exp-name', experimentName);
    await page.selectOption('#exp-strategy', 'hash');
    await page.getByRole('button', { name: 'Create experiment' }).click();

    // Real POST /demo/v1/experiments -> success banner + a new list row.
    await expect(page.locator('[data-notice]')).toContainText(experimentName, { timeout: 15000 });
    const row = page.locator('tbody tr', { hasText: experimentName }).first();
    await expect(row).toBeVisible();
    await expect(row.locator('.pill')).toHaveText('hash');

    experimentId = (await row.locator('td.mono').first().innerText()).trim();
    expect(experimentId).toMatch(/^\d+$/);
  });

  test('expands the created experiment to show its two variants', async ({ page }) => {
    await page.goto(`/${TENANT}/experiments`);
    const row = page.locator('tbody tr', { hasText: experimentName }).first();
    await row.getByRole('button', { name: 'Variants' }).click();

    // Real GET /demo/v1/experiments/{id} renders the variant detail table.
    const detail = page.locator(`[data-detail="${experimentId}"]`);
    await expect(detail).toBeVisible();
    await expect(detail.locator('tbody tr')).toHaveCount(2);
    await expect(detail).toContainText('control');
    await expect(detail).toContainText('treatment');
    await expect(detail).toContainText('default');
  });

  test('shows results on the analytics page', async ({ page }) => {
    await page.goto(`/${TENANT}/analytics?experiment=${experimentId}`);

    // Real GET /demo/v1/experiments/{id}/results. No tracking has happened, so every
    // variant reports zero and there are no orphan conversions.
    await expect(page.locator('[data-stat-id]')).toHaveText(experimentId, { timeout: 15000 });
    await expect(page.locator('[data-stat-orphan]')).toHaveText('0');

    const rows = page.locator('[data-results-table] tbody tr');
    await expect(rows).toHaveCount(2);
    await expect(rows.first()).toContainText('0.00%');

    // The selector should have preselected the linked experiment.
    await expect(page.locator('[data-experiment-select]')).toHaveValue(experimentId);
  });

  test('navigates from a list row to that experiment’s analytics', async ({ page }) => {
    await page.goto(`/${TENANT}/experiments`);
    const row = page.locator('tbody tr', { hasText: experimentName }).first();
    await row.getByRole('link', { name: 'Analytics' }).click();

    await page.waitForURL(new RegExp(`/${TENANT}/analytics\\?experiment=${experimentId}$`));
    await expect(page.locator('[data-stat-id]')).toHaveText(experimentId, { timeout: 15000 });
  });

  test('edits the experiment through the form', async ({ page }) => {
    await page.goto(`/${TENANT}/experiments`);
    const row = page.locator('tbody tr', { hasText: experimentName }).first();
    await row.getByRole('button', { name: 'Edit' }).click();

    // The shared form switches to edit mode, prefilled from GET /experiments/{id}.
    await expect(page.locator('[data-form-title]')).toContainText(`#${experimentId}`);
    await expect(page.locator('#exp-name')).toHaveValue(experimentName);
    const allocs = page.locator('.variant-row .v-alloc');
    await expect(allocs).toHaveCount(2);

    // Re-split the traffic 70/30 and rename the control, then save.
    await page.locator('.variant-row .v-content').first().fill('control-edited');
    await allocs.nth(0).fill('70');
    await allocs.nth(1).fill('30');
    await expect(page.locator('[data-alloc-total]')).toContainText('100%');
    await page.getByRole('button', { name: 'Save changes' }).click();

    // Real PUT /demo/v1/experiments/{id} -> success banner, form back to create mode.
    await expect(page.locator('[data-notice]')).toContainText('Updated', { timeout: 15000 });
    await expect(page.locator('[data-form-title]')).toHaveText('Create experiment');

    // The change is persisted: expanding the row reflects the new split + content.
    const updatedRow = page.locator('tbody tr', { hasText: experimentName }).first();
    await updatedRow.getByRole('button', { name: 'Variants' }).click();
    const detail = page.locator(`[data-detail="${experimentId}"]`);
    await expect(detail).toBeVisible();
    await expect(detail).toContainText('control-edited');
    await expect(detail).toContainText('70.00%'); // alloc_pct is a scale-2 BigDecimal
    await expect(detail).toContainText('30.00%');
  });

  test('deletes the experiment through the UI', async ({ page }) => {
    await page.goto(`/${TENANT}/experiments`);
    const row = page.locator('tbody tr', { hasText: experimentName }).first();

    // Accept the real confirm() dialog the browser raises.
    page.once('dialog', (dialog) => dialog.accept());
    await row.getByRole('button', { name: 'Delete' }).click();

    // Real DELETE /demo/v1/experiments/{id} -> banner + the row is gone.
    await expect(page.locator('[data-notice]')).toContainText('Deleted', { timeout: 15000 });
    await expect(page.locator('tbody tr', { hasText: experimentName })).toHaveCount(0);
  });
});
