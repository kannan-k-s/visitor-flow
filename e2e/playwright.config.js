// Playwright config for the admin SPA browser tests.
//
// Target is the local nginx origin on port 80 (no port in the URL) — the same
// entry point a real operator uses. The Spring app must be up on 8080 and the
// infra stack (nginx, mysql, redis, rabbitmq) running; see README.md.
const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  // The authenticated suite creates/deletes one experiment in order, so keep it serial.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 30000,
  expect: { timeout: 10000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'off'
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } }
  ]
});
