import { defineConfig } from '@playwright/test';

/**
 * E2E tests need the full stack: MySQL + the Spring backend on :8080.
 * Locally: `./start-local.sh` (or run the backend yourself), then `npm run e2e`.
 * CI boots both (see .github/workflows/ci.yml, job "e2e").
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm start',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
