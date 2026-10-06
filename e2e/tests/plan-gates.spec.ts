import { expect, test } from '@playwright/test';
import { apiResponse, connectProSources, login, seed } from './support.js';

for (const tenant of seed.tenants) {
  test(`${tenant.plan}: permisos reales de analytics, integraciones y Webhooks`, async ({ page }) => {
    const headers = await login(page, tenant);
    const advanced = tenant.plan === 'PRO';
    const analyticsLoaded = apiResponse(page, '/analytics/resumen');
    await page.goto('/app/analitica');
    const analyticsResponse = await analyticsLoaded;
    // E-02 permite el resumen básico en FREE. Solo el bloque avanzado se restringe.
    expect(analyticsResponse.status()).toBe(200);
    const analytics = await analyticsResponse.json();
    expect(analytics).toMatchObject({ plan: tenant.plan, analiticaAvanzada: advanced });
    if (advanced) {
      expect(analytics.pro).toBeTruthy();
      await expect(page.getByRole('heading', { name: 'Recomendaciones por producto' })).toBeVisible();
    } else {
      expect(analytics.pro == null).toBe(true);
      await expect(page.getByRole('heading', { name: 'Analítica avanzada disponible con Pro' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Recomendaciones por producto' })).not.toBeVisible();
    }

    if (advanced) {
      await connectProSources(page);
    } else {
      await page.goto('/app/integraciones');
      for (const [source, label] of [['BANCO', 'Core bancario'], ['WALLET', 'Billetera móvil']]) {
        await expect(page.getByRole('button', { name: `Conectar: ${label}`, exact: true })).toBeDisabled();
        const denied = await page.request.put(`/api/v1/integraciones/${source}`, { headers, data: { activa: true } });
        expect(denied.status()).toBe(409);
        expect(await denied.json()).toMatchObject({ code: 'PLAN_LIMIT_REACHED' });
      }
    }
    const integrations = await page.request.get('/api/v1/integraciones', { headers });
    expect(integrations.status()).toBe(200);
    const catalog = await integrations.json();
    expect(catalog).toEqual(expect.arrayContaining([expect.objectContaining({ fuente: 'BURO', estado: 'CONECTADO' })]));
    for (const source of ['BANCO', 'WALLET']) {
      expect(catalog).toEqual(expect.arrayContaining([expect.objectContaining({
        fuente: source, estado: advanced ? 'CONECTADO' : 'REQUIERE_PRO',
      })]));
    }

    await page.goto('/app/desarrolladores');
    await expect(page.getByRole('heading', { name: 'Webhooks', exact: true })).toBeVisible();
    const webhooks = await page.request.get('/api/v1/webhooks', { headers });
    // E-08 pendiente: un 404 falla aquí. No se omite la prueba ni se acepta como bloqueo de plan.
    expect(webhooks.status(), 'GET /webhooks debe estar integrado desde E-08').toBe(advanced ? 200 : 403);
    if (advanced) {
      expect(webhooks.headers()['content-type']).toContain('application/json');
      await webhooks.json();
    } else {
      expect(await webhooks.json()).toMatchObject({ code: 'FEATURE_NOT_IN_PLAN' });
    }
  });
}
