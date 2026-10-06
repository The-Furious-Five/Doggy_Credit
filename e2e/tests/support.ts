import { readFileSync } from 'node:fs';
import { expect, type Page } from '@playwright/test';
import { apiResponse } from './api-response.js';
import { installApiPacing } from './api-pacing.js';

export { apiRequest, apiResponse } from './api-response.js';

export interface Tenant {
  id: string;
  name: string;
  plan: 'FREE' | 'PRO';
  username: string;
  passwordEnv: string;
}

// Archivo ignorado, generado por scripts/generate_e2e_fixture.py desde seed/tenants.yaml.
export const seed: { tenants: Tenant[]; document: string } = JSON.parse(
  readFileSync(new URL('../.generated/seed.json', import.meta.url), 'utf8'),
);

export async function login(page: Page, tenant: Tenant): Promise<{ Authorization: string }> {
  const password = process.env[tenant.passwordEnv];
  if (!password) throw new Error(`Falta la variable de credenciales de demo ${tenant.passwordEnv}`);
  await page.goto('/acceso');
  await installApiPacing(page, tenant);
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
  await expect(page).toHaveURL(/\/auth\/realms\/doggycredit\/protocol\/openid-connect\/auth\?/);
  const authorizationUrl = new URL(page.url());
  expect(authorizationUrl.searchParams.get('code_challenge_method')).toBe('S256');
  expect(authorizationUrl.searchParams.get('code_challenge')).toBeTruthy();
  await page.locator('#username').fill(tenant.username);
  await page.locator('#password').fill(password);
  const response = await apiResponse(page, '/tenant/actual', () => page.locator('#kc-login').click());
  expect(response.status(), 'La sesión debe cargar el tenant real').toBe(200);
  expect(await response.json()).toMatchObject({ id: tenant.id, plan: tenant.plan, nombre: tenant.name });
  await expect(page).toHaveURL(/\/app\/resumen(?:[?#]|$)/);
  const authorization = await response.request().headerValue('authorization');
  // Comprobar solo la presencia: los mensajes de fallo nunca imprimen el token.
  expect(Boolean(authorization?.startsWith('Bearer '))).toBe(true);
  return { Authorization: authorization! };
}

export async function connectProSources(page: Page): Promise<void> {
  await page.goto('/app/integraciones');
  for (const [source, label] of [['BANCO', 'Core bancario'], ['WALLET', 'Billetera móvil']]) {
    const card = page.locator('ds-card').filter({ has: page.getByRole('heading', { name: label, exact: true }) });
    const connect = card.getByRole('button', { name: `Conectar: ${label}`, exact: true });
    // Suite repetible: una fuente ya conectada no necesita otra mutación.
    await expect(card.getByRole('button')).toBeVisible();
    if (await connect.count()) {
      const saved = await apiResponse(page, `/integraciones/${source}`, () => connect.click(), 'PUT');
      expect(saved.status()).toBe(200);
    }
    await expect(card.getByRole('button', { name: `Desconectar: ${label}`, exact: true })).toBeEnabled();
  }
}
