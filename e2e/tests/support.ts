import { readFileSync } from 'node:fs';
import { test as base, expect, type Page } from '@playwright/test';
import { parse } from 'yaml';

export type Plan = 'FREE' | 'PRO';

export interface DemoAdmin {
  readonly plan: Plan;
  readonly tenant: string;
  readonly username: string;
  readonly password: string;
}

interface SeedUser {
  username: string;
  roles: string[];
  password_env: string;
}

interface SeedTenant {
  nombre: string;
  plan: Plan;
  usuarios: SeedUser[];
}

// seed/tenants.yaml es la única fuente de tenants y usuarios de demo: no se copian aquí.
const seed = parse(readFileSync(new URL('../../seed/tenants.yaml', import.meta.url), 'utf8')) as {
  tenants: SeedTenant[];
};

/** TENANT_ADMIN del primer tenant de demo con ese plan; la contraseña sale del entorno (.env). */
export function adminOf(plan: Plan): DemoAdmin {
  const tenant = seed.tenants.find((t) => t.plan === plan);
  const admin = tenant?.usuarios.find((u) => u.roles.includes('TENANT_ADMIN'));
  if (!tenant || !admin) throw new Error(`seed/tenants.yaml no tiene un TENANT_ADMIN con plan ${plan}`);
  const password = process.env[admin.password_env];
  if (!password) throw new Error(`Falta ${admin.password_env} en el entorno (ver e2e/README.md)`);
  return { plan, tenant: tenant.nombre, username: admin.username, password };
}

/** Login real: SPA → Keycloak (PKCE) → callback → consola. */
export async function login(page: Page, admin: DemoAdmin): Promise<void> {
  await page.goto('/acceso');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.locator('#username').fill(admin.username);
  await page.locator('#password').fill(admin.password);
  await page.locator('#kc-login').click();
  await expect(page).toHaveURL(/\/app\/resumen$/);
  await expect(page.getByText(admin.tenant, { exact: true })).toBeVisible();
  const sidebar = page.getByRole('complementary');
  await expect(sidebar.getByText(admin.plan === 'PRO' ? 'Plan Pro' : 'Plan Gratis', { exact: true })).toBeVisible();
}

/** Navega por el menú lateral, como lo haría el usuario (sin recargar la SPA). */
export async function openFromMenu(page: Page, label: string): Promise<void> {
  await page.getByRole('navigation', { name: 'Consola' }).getByRole('link', { name: label, exact: true }).click();
}

/** Tarjeta de un conector en /app/integraciones. */
export function connectorCard(page: Page, name: string) {
  return page.locator('ds-card').filter({ has: page.getByRole('heading', { name, exact: true }) });
}

/** Activa un conector desde Integraciones; si ya estaba conectado (base reutilizada), lo deja así. */
export async function connect(page: Page, name: string): Promise<void> {
  const card = connectorCard(page, name);
  const action = card.getByRole('button', { name: new RegExp(`^(Conectar|Desconectar): ${name}$`) });
  if ((await action.textContent())?.trim() === 'Conectar') await action.click();
  await expect(card.getByText('Conectado', { exact: true })).toBeVisible();
}

/**
 * Cada test falla si la API respondió 5xx o 429: un error del servidor que la
 * pantalla oculte con un estado vacío no debe pasar inadvertido.
 */
export const test = base.extend<{ apiErrors: string[] }>({
  apiErrors: [
    async ({ page }, use) => {
      const errors: string[] = [];
      page.on('response', (response) => {
        const status = response.status();
        if (response.url().includes('/api/v1/') && (status >= 500 || status === 429)) {
          errors.push(`${status} ${response.request().method()} ${new URL(response.url()).pathname}`);
        }
      });
      await use(errors);
      expect(errors, 'respuestas 5xx/429 de la API durante el escenario').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };
