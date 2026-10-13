import { createHmac } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { test as base, expect, type Browser, type Page } from '@playwright/test';
import { parse } from 'yaml';

export type Plan = 'FREE' | 'PRO';

export interface DemoAdmin {
  readonly plan: Plan;
  readonly tenant: string;
  readonly username: string;
  readonly password: string;
}

/** Cualquier usuario de demo (admin, analista u operador), con el plan de su tenant. */
export type DemoUser = DemoAdmin;

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

function demoUser(tenant: SeedTenant, user: SeedUser): DemoUser {
  const password = process.env[user.password_env];
  if (!password) throw new Error(`Falta ${user.password_env} en el entorno (ver e2e/README.md)`);
  return { plan: tenant.plan, tenant: tenant.nombre, username: user.username, password };
}

/** Usuario con ese rol del primer tenant de demo con ese plan; la contraseña sale del entorno (.env). */
function userOf(plan: Plan, role: string): DemoUser {
  const tenant = seed.tenants.find((t) => t.plan === plan && t.usuarios.some((u) => u.roles.includes(role)));
  const user = tenant?.usuarios.find((u) => u.roles.includes(role));
  if (!tenant || !user) throw new Error(`seed/tenants.yaml no tiene un ${role} con plan ${plan}`);
  return demoUser(tenant, user);
}

/** TENANT_ADMIN del primer tenant de demo con ese plan. */
export function adminOf(plan: Plan): DemoAdmin {
  return userOf(plan, 'TENANT_ADMIN');
}

/** ANALYST del primer tenant de demo con ese plan. */
export function analystOf(plan: Plan): DemoUser {
  return userOf(plan, 'ANALYST');
}

/** El operador de la plataforma (PLATFORM_ADMIN) que cambia planes: lo siembra A-11 (LOS-115). */
export function operator(): DemoUser {
  for (const tenant of seed.tenants) {
    const user = tenant.usuarios.find((u) => u.roles.includes('PLATFORM_ADMIN'));
    if (user) return demoUser(tenant, user);
  }
  throw new Error('seed/tenants.yaml no tiene un usuario PLATFORM_ADMIN (ver docs/runbooks/e2e-flujos-sprint3.md)');
}

/**
 * E-12: escenarios que esperan historias de otros. Quien fusiona la historia pone su
 * valor en true en el mismo PR y desde ahí el escenario corre en el CI. Para probar
 * antes en local: `E2E_DEPENDENCIAS=bandeja,cambioDePlan npx playwright test`.
 */
const merged = {
  /** C-10 (LOS-111): el backend implementa RevisionManualApi (GET y POST /revision-manual). */
  bandeja: true,
  /** A-11 (LOS-115): seed/tenants.yaml siembra un operador PLATFORM_ADMIN (y su contraseña en .env.example). */
  cambioDePlan: true,
};
const enabled = new Set((process.env['E2E_DEPENDENCIAS'] ?? '').split(',').map((name) => name.trim()));

export function ready(dependency: keyof typeof merged): boolean {
  return merged[dependency] || enabled.has(dependency);
}

/**
 * A-14 (LOS-118): los TENANT_ADMIN sembrados configuran MFA en su primer inicio de sesión
 * y desde ahí Keycloak les pide el código cada vez. El secreto queda fuera del repositorio
 * (e2e/.generated, el mismo archivo que usa identity_smoke.py) para poder volver a entrar
 * mientras viva el volumen de Keycloak.
 */
const TOTP_DIR = new URL('../.generated/', import.meta.url);
const TOTP_STORE = new URL('totp.json', TOTP_DIR);
const TOTP_STEP_MS = 30_000;

type TotpStore = Record<string, { secret: string; counter: number }>;

function totpStore(): TotpStore {
  return existsSync(TOTP_STORE) ? (JSON.parse(readFileSync(TOTP_STORE, 'utf8')) as TotpStore) : {};
}

function totp(secret: string, counter: number): string {
  const message = Buffer.alloc(8);
  message.writeBigUInt64BE(BigInt(counter));
  // Keycloak firma con los bytes del secreto tal cual; el Base32 es solo lo que muestra al usuario.
  const digest = createHmac('sha1', Buffer.from(secret, 'utf8')).update(message).digest();
  const offset = digest[digest.length - 1] & 0x0f;
  return ((digest.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).toString().padStart(6, '0');
}

/**
 * Keycloak no acepta dos veces el mismo código: se usa el del paso siguiente al último
 * usado (su ventana admite uno de adelanto) y, si aún no vale, se espera.
 */
async function nextTotp(page: Page, key: string, secret: string): Promise<string> {
  const store = totpStore();
  const counter = Math.max(Math.floor(Date.now() / TOTP_STEP_MS), (store[key]?.counter ?? 0) + 1);
  const wait = (counter - 1) * TOTP_STEP_MS - Date.now();
  if (wait > 0) await page.waitForTimeout(wait + 1_000);
  store[key] = { secret, counter };
  mkdirSync(TOTP_DIR, { recursive: true });
  writeFileSync(TOTP_STORE, JSON.stringify(store, null, 2));
  return totp(secret, counter);
}

/** Login real: SPA → Keycloak (PKCE, y MFA si el usuario lo tiene) → callback → consola. */
export async function login(page: Page, admin: DemoUser): Promise<void> {
  await page.goto('/acceso');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.locator('#username').fill(admin.username);
  await page.locator('#password').fill(admin.password);
  await page.locator('#kc-login').click();

  // Tras la contraseña: configurar MFA (primer ingreso), dar el código, o ya la consola.
  const setup = page.locator('#totp');
  const challenge = page.locator('#otp');
  await expect(setup.or(challenge).or(page.getByRole('navigation', { name: 'Consola' })).first()).toBeVisible();
  const key = `${new URL(page.url()).origin}|${admin.username}`;
  if (await setup.isVisible()) {
    const secret = await page.locator('#totpSecret').getAttribute('value');
    if (!secret) throw new Error('La pantalla de configuración MFA de Keycloak no trae el secreto');
    await setup.fill(await nextTotp(page, key, secret));
    await page.locator('#userLabel').fill('e2e');
    await page.locator('#saveTOTPBtn').click();
  } else if (await challenge.isVisible()) {
    const saved = totpStore()[key];
    if (!saved) {
      throw new Error(
        `Keycloak pide el código MFA de ${admin.username} y e2e/.generated/totp.json no tiene su secreto: ` +
          'recrea el volumen de Keycloak (docker compose down -v). Ver e2e/README.md',
      );
    }
    await challenge.fill(await nextTotp(page, key, saved.secret));
    await page.locator('#kc-login').click();
  }

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
 * Nueva evaluación de un titular sintético con solo el buró autorizado (alcanza en
 * cualquier plan). Devuelve el solicitante y la decisión que muestra el resultado.
 */
export async function evaluate(page: Page, document: string): Promise<{ applicantId: string; decision: string }> {
  await openFromMenu(page, 'Nueva evaluación');
  await page.getByRole('textbox', { name: 'Documento de identidad o NIT' }).fill(document);
  await page.getByRole('textbox', { name: 'Actividad económica' }).fill('Comercio minorista');
  await page.getByRole('spinbutton', { name: 'Ingreso mensual declarado' }).fill('4200');
  await page.getByRole('spinbutton', { name: 'Monto solicitado' }).fill('12000');
  await page.getByRole('combobox', { name: 'Destino del crédito' }).click();
  await page.getByRole('option', { name: 'Capital de trabajo' }).click();
  await page.getByRole('button', { name: 'Continuar' }).click();
  await page.getByRole('checkbox', { name: 'Consultar burós de crédito' }).check();
  await page.getByRole('button', { name: 'Continuar' }).click();
  await expect(page.getByRole('heading', { name: 'Fuentes', level: 2 })).toBeVisible();
  await page.getByRole('button', { name: 'Evaluar' }).click();
  await expect(page.getByRole('heading', { name: 'Resultado', level: 2 })).toBeVisible();
  const decision = (await page.locator('ds-stat').filter({ hasText: 'Decisión' }).innerText()).replace(/\s+/g, ' ');
  const href = await page.getByRole('link', { name: 'Ver perfil completo' }).getAttribute('href');
  const applicantId = href?.match(/\/app\/clientes\/([0-9a-f-]{36})$/)?.[1];
  if (!applicantId) throw new Error(`El resultado no enlaza al solicitante (href=${href})`);
  return { applicantId, decision };
}

/** Lo que anuncia el toast: su región aria-live (la que oyen los lectores de pantalla). */
export function toast(page: Page, message: string) {
  return page.getByRole('status').filter({ hasText: message });
}

/** Cambia el plan de un tenant desde Administración de tenants (vista del operador). */
export async function changePlan(page: Page, tenant: string, plan: 'Gratis' | 'Pro'): Promise<void> {
  await page.goto('/app/admin/tenants');
  const row = page.getByRole('row').filter({ has: page.getByRole('rowheader', { name: tenant, exact: true }) });
  await row.getByRole('combobox', { name: `Plan para ${tenant}` }).click();
  await page.getByRole('option', { name: plan, exact: true }).click();
  await row.getByRole('button', { name: 'Guardar plan' }).click();
  await expect(toast(page, 'Plan actualizado.')).toBeVisible();
}

/** Respuestas 5xx/429 de la API vistas por una página. */
export function trackApiErrors(page: Page, errors: string[]): void {
  page.on('response', (response) => {
    const status = response.status();
    if (response.url().includes('/api/v1/') && (status >= 500 || status === 429)) {
      errors.push(`${status} ${response.request().method()} ${new URL(response.url()).pathname}`);
    }
  });
}

/**
 * Otra sesión en su propio contexto (otro usuario a la vez que `page`), con la misma
 * configuración del proyecto y vigilando también los errores de la API.
 */
export async function session(browser: Browser, user: DemoUser, errors: string[]): Promise<Page> {
  const { baseURL, ignoreHTTPSErrors, locale } = test.info().project.use;
  const page = await (await browser.newContext({ baseURL, ignoreHTTPSErrors, locale })).newPage();
  trackApiErrors(page, errors);
  await login(page, user);
  return page;
}

/**
 * Cada test falla si la API respondió 5xx o 429: un error del servidor que la
 * pantalla oculte con un estado vacío no debe pasar inadvertido.
 */
export const test = base.extend<{ apiErrors: string[] }>({
  apiErrors: [
    async ({ page }, use) => {
      const errors: string[] = [];
      trackApiErrors(page, errors);
      await use(errors);
      expect(errors, 'respuestas 5xx/429 de la API durante el escenario').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };
