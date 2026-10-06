import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './regression',
  timeout: 15_000,
  workers: 1,
  fullyParallel: false,
  forbidOnly: Boolean(process.env['CI']),
  retries: 0,
  reporter: 'list',
  outputDir: 'test-results/regression',
  use: { trace: 'off', video: 'off', screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
