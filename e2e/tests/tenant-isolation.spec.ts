import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { login, seed } from './support.js';

test('FREE y PRO aíslan el mismo documento por tenant en API y perfil', async ({ browser, baseURL }) => {
  // Documento independiente del alta del flujo: ningún spec depende del orden de los demás.
  const document = `${seed.document}-ISO-${randomUUID().slice(0, 8)}`.toUpperCase();
  const sessions = [];
  try {
    for (const tenant of seed.tenants) {
      const context = await browser.newContext({ baseURL, ignoreHTTPSErrors: true });
      sessions.push({ context, tenant, page: await context.newPage(), headers: {} as { Authorization: string }, id: '' });
      const session = sessions[sessions.length - 1];
      session.headers = await login(session.page, tenant);
      const created = await context.request.post('/api/v1/solicitantes', {
        headers: session.headers,
        data: { documento: document, actividad: 'Comercio (demo E2E)', antiguedadActividadMeses: 60,
          ingresosMensuales: '9000', moneda: 'BOB', destinoCredito: 'Capital de trabajo', segmento: 'PERSONA' },
      });
      expect(created.status()).toBe(201);
      session.id = (await created.json()).id;
      expect(session.id).toBeTruthy();
    }
    expect(sessions[0].id).not.toBe(sessions[1].id);
    for (let index = 0; index < sessions.length; index++) {
      const own = sessions[index];
      const other = sessions[1 - index];
      const actual = await own.context.request.get('/api/v1/tenant/actual', {
        headers: { ...own.headers, 'X-Tenant-Id': other.tenant.id },
      });
      expect(actual.status()).toBe(200);
      expect((await actual.json()).id).toBe(own.tenant.id);
      const allowed = await own.context.request.get(`/api/v1/solicitantes/${own.id}`, { headers: own.headers });
      expect(allowed.status()).toBe(200);
      expect(await allowed.json()).toMatchObject({ id: own.id, documento: document });
      const foreign = await own.context.request.get(`/api/v1/solicitantes/${other.id}`, { headers: own.headers });
      expect(foreign.status()).toBe(404);
      await own.page.goto(`/app/clientes/${other.id}`);
      await expect(own.page.getByRole('heading', { name: 'Cliente no encontrado', exact: true })).toBeVisible();
      await expect(own.page.getByRole('heading', { name: document, exact: true })).not.toBeVisible();
    }
  } finally {
    for (const session of sessions) await session.context.close();
  }
});
