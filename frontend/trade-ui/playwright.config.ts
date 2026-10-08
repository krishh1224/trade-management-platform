/// <reference types="node" />

import { defineConfig } from '@playwright/test';
import * as dotenv from 'dotenv';
import * as path from 'path';

// Load .env from project root
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:4200';

// Optional: pause this many milliseconds between browser actions so a headed run can be watched,
// e.g. `$env:E2E_SLOW_MO=800; npx playwright test --headed`. Unset means full speed.
const slowMo = Number(process.env.E2E_SLOW_MO ?? 0);

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  // When slowed down, run one test at a time (one window) and allow the extra time it takes.
  workers: slowMo > 0 ? 1 : undefined,
  timeout: slowMo > 0 ? 180000 : 30000,
  use: {
    baseURL,
    trace: 'on-first-retry',
    launchOptions: { slowMo }
  },
  webServer: {
    command: 'npm run start -- --host localhost --port 4200',
    url: baseURL,
    reuseExistingServer: true,
    timeout: 120000
  }
});
