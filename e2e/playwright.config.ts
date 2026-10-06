import { defineConfig, devices } from '@playwright/test';

/**
 * E-06: flujo completo contra Compose (Caddy + SPA + API + Keycloak + simuladores).
 * Levantar antes el stack; ver e2e/README.md.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  // Un solo worker: los escenarios comparten tenants de demo y una base real.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env['CI'],
  retries: 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'https://localhost',
    // Certificado de la CA interna de Caddy.
    ignoreHTTPSErrors: true,
    locale: 'es-BO',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
