import { readFileSync } from 'node:fs';
import { expect, type Page, type Response } from '@playwright/test';

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

export function apiResponse(page: Page, path: string, method = 'GET'): Promise<Response> {
  return page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/v1${path}` && response.request().method() === method,
  );
}

export async function login(page: Page, tenant: Tenant): Promise<{ Authorization: string }> {
  const password = process.env[tenant.passwordEnv];
  if (!password) throw new Error(`Falta la variable de credenciales de demo ${tenant.passwordEnv}`);
  await page.goto('/acceso');
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
  await expect(page).toHaveURL(/\/auth\/realms\/doggycredit\/protocol\/openid-connect\/auth\?/);
  const authorizationUrl = new URL(page.url());
  expect(authorizationUrl.searchParams.get('code_challenge_method')).toBe('S256');
  expect(authorizationUrl.searchParams.get('code_challenge')).toBeTruthy();
  await page.locator('#username').fill(tenant.username);
  await page.locator('#password').fill(password);
  const currentTenant = apiResponse(page, '/tenant/actual');
  await page.locator('#kc-login').click();
  const response = await currentTenant;
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
      const saved = apiResponse(page, `/integraciones/${source}`, 'PUT');
      await connect.click();
      expect((await saved).status()).toBe(200);
    }
    await expect(card.getByRole('button', { name: `Desconectar: ${label}`, exact: true })).toBeEnabled();
  }
}
