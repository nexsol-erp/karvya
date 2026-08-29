import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end configuration.
 *
 * <p>Runs against the development stack rather than starting its own: the
 * backend needs a database, a mail sink and seeded data, and reproducing that
 * from Playwright would be a second, divergent way to boot the application.
 * `reuseExistingServer` means an already-running dev server is used as-is.
 *
 * <p>Serial, not parallel. These place real orders against a shared database,
 * so two specs racing would consume each other's stock.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],

  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 15_000,
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],

  // Only start the dev server when no base URL was given. With E2E_BASE_URL
  // set - CI, or a local run against the Docker stack - the application is
  // already up and starting Vite would test the wrong thing.
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'npm run dev',
        url: 'http://localhost:5173',
        reuseExistingServer: true,
        timeout: 120_000,
      },
});
