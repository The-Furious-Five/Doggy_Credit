import type { Page } from '@playwright/test';
import { adminOf, captureApi, evaluate, expect, login, openFromMenu, ready, test, toast } from './support';

// RF-ANA-02 (C-14 / D-13). Titular sintético «moroso»: ninguna política lo preaprueba y
// queda en revisión manual (C-05), igual que en manual-review.spec.ts.
const DOCUMENT = 'CI-DEMO-0001002';
const JUSTIFICATION = 'Rechazo por mora vigente en el buró; se revisará en 6 meses.';

interface Override {
  overrides: number;
  evaluaciones: number;
  porcentaje: number;
}

/** Abre Analítica y devuelve la tasa de override que la API le entregó a la vista. */
async function overrideRate(page: Page): Promise<Override> {
  const summary = page.waitForResponse((r) => new URL(r.url()).pathname.endsWith('/api/v1/analytics/resumen'));
  await openFromMenu(page, 'Resumen');
  await openFromMenu(page, 'Analítica');
  const body = (await (await summary).json()) as { pro?: { tasaOverride?: Override } };
  expect(body.pro?.tasaOverride, 'AnalyticsPro.tasaOverride (C-14)').toBeDefined();
  return body.pro!.tasaOverride!;
}

test('Pro: resolver un caso en revisión manual actualiza la tasa de override en Analítica', async ({ page }) => {
  test.fixme(
    !ready('kpisCartera'),
    'Espera C-14 (LOS-130) y D-13 (LOS-133): KPIs de cartera. Ver docs/runbooks/e2e-flujos-sprint4.md',
  );
  await login(page, adminOf('PRO'));
  const before = await overrideRate(page);

  // Un caso nuevo en la bandeja, resuelto con override justificado.
  const { applicantId, decision } = await evaluate(page, DOCUMENT);
  expect(decision, 'el titular moroso queda en revisión manual').toContain('Revisión manual');
  await openFromMenu(page, 'Revisión manual');
  const row = page.locator('tbody tr').filter({ has: page.locator(`a[href="/app/clientes/${applicantId}"]`) }).first();
  await row.getByRole('button', { name: /^Resolver caso de / }).click();
  const dialog = page.getByRole('dialog', { name: 'Resolver caso' });
  await dialog.getByRole('radio', { name: 'Rechazar' }).check();
  await dialog.getByRole('textbox', { name: 'Justificación' }).fill(JUSTIFICATION);
  await dialog.getByRole('button', { name: 'Resolver caso' }).click();
  await expect(dialog).toBeHidden();
  await expect(toast(page, 'Caso rechazado')).toBeVisible();

  // La evaluación y su override entran al intervalo por defecto (el mes en curso).
  const after = await overrideRate(page);
  expect(after.overrides, 'un override más').toBe(before.overrides + 1);
  expect(after.evaluaciones, 'una evaluación más en la base').toBe(before.evaluaciones + 1);
  expect(after.porcentaje).toBeCloseTo((after.overrides / after.evaluaciones) * 100, 1);

  // La vista muestra la tasa nueva junto a la cantidad de overrides.
  const kpi = page.locator('ds-stat').filter({ hasText: 'Tasa de override' });
  await expect(kpi).toBeVisible();
  await expect(kpi).toContainText(String(after.overrides));
});

test('Gratis: Analítica invita a Pro en lugar de mostrar un error', async ({ page }) => {
  await login(page, adminOf('FREE'));
  const responses = await captureApi(page, (status, _method, url) => status >= 400 || url.includes('/analytics/resumen'));
  await openFromMenu(page, 'Analítica');

  await expect(page.getByRole('heading', { name: 'Analítica', level: 1 })).toBeVisible();
  await expect.poll(() => responses.some((r) => r.path.endsWith('/analytics/resumen'))).toBe(true);
  const summary = responses.find((r) => r.path.endsWith('/analytics/resumen'))!;
  expect(summary.status, 'el resumen básico responde en todos los planes').toBe(200);
  expect((summary.body as { pro?: unknown }).pro, 'sin KPIs Pro en el plan Gratis').toBeUndefined();
  await expect(page.getByText('Analítica avanzada disponible con Pro')).toBeVisible();
  await expect(page.getByText('Tasa de override')).toHaveCount(0);
  await expect(page.getByRole('alert').filter({ hasText: /error|no pudimos|no se pudo/i })).toHaveCount(0);
  expect(responses.filter((r) => r.status >= 400), 'ninguna petición de Analítica falla').toEqual([]);
});
