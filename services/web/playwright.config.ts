import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against a running stack.
 *
 * <p>These are deliberately NOT wired into `make test`. They need four containers and a real
 * database, and a suite that silently passes when the stack happens to be down is worse than no
 * suite. `make e2e` brings up its own throwaway stack (`finances-e2e`, ports 4201/8081) and runs
 * against that — never the dev stack `make up` starts, whose database holds real data.
 *
 * <p>The base URL is the web container, not the API: going through nginx exercises the same proxy
 * path a browser uses, which is where cookie and CSRF problems actually live.
 */
export default defineConfig({
  testDir: './e2e',
  // Every run starts from an empty database. Without it the suite is stateful across runs — and a
  // broken run leaves the test account locked out, which then looks like a credentials bug.
  globalSetup: './e2e/reset-database.ts',
  globalTeardown: './e2e/clear-database.ts',
  fullyParallel: false, // one database; tests share and reset it
  workers: 1,
  timeout: 30_000,
  retries: 0,
  reporter: [['list']],
  use: {
    // Defaults to the isolated e2e stack (`make e2e`), not the dev stack on 4200. A bare
    // `npx playwright test` therefore cannot reach the database Allen is using.
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4201',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
