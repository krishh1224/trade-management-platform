/// <reference types="node" />

import { defineConfig } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:4200';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  use: {
    baseURL,
    trace: 'on-first-retry'
  },
  webServer: {
    command: 'npm run start -- --host localhost --port 4200',
    url: baseURL,
    reuseExistingServer: true,
    timeout: 120000
  }
});