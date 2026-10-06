import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  // Cada test crea su sesión. Un solo worker evita carreras sobre los datos de demo.
  workers: 1,
  fullyParallel: false,
  forbidOnly: Boolean(process.env['CI']),
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  outputDir: 'test-results',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'https://localhost',
    // Compose usa la CA local de Caddy; la comprobación de salud sí verifica su CA.
    ignoreHTTPSErrors: true,
    screenshot: 'only-on-failure',
    // No persistir JWT, formularios de login ni cuerpos de peticiones en trazas.
    trace: 'off',
    video: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
