import type { Page } from '@playwright/test';
import { adminOf, analystOf, evaluate, expect, login, ready, test, type DemoUser } from './support';

// RF-REC-02 / RF-REC-03 (E-16). Titular sintético «buen pagador»: cumple los criterios
// de al menos un producto del catálogo de demo.
const DOCUMENT = 'CI-DEMO-0001001';

interface Assessment {
  elegibilidad?: { elegible: boolean; criterios: unknown[] }[];
  recomendaciones?: { montoSugerido: string; plazoSugeridoMeses?: number }[];
}

/** Evalúa desde la consola y devuelve también la evaluación tal como la entregó la API. */
async function evaluateAs(page: Page, user: DemoUser): Promise<Assessment> {
  await login(page, user);
  let assessment: Assessment | undefined;
  await page.route('**/api/v1/credit-assessments', async (route) => {
    const upstream = await route.fetch();
    if (route.request().method() === 'POST' && upstream.ok()) assessment = (await upstream.json()) as Assessment;
    await route.fulfill({ response: upstream });
  });
  await evaluate(page, DOCUMENT);
  expect(assessment, 'la consola creó la evaluación').toBeDefined();
  return assessment!;
}

/** Productos elegibles del resultado, cada uno con sus seis criterios marcados ✓ o ✗. */
async function expectEligibleProducts(page: Page, assessment: Assessment): Promise<void> {
  expect(assessment.elegibilidad?.length, 'snapshot de elegibilidad en todos los planes').toBeGreaterThan(0);
  const section = page.getByRole('region', { name: 'Productos elegibles' });
  await expect(section).toBeVisible();
  await expect(section.getByText('✓').first()).toBeVisible();
  const anyFailed = assessment.elegibilidad!.some((product) => !product.elegible);
  if (anyFailed) await expect(section.getByText('✗').first()).toBeVisible();
}

test('Gratis: el resultado muestra los productos elegibles con sus criterios y la invitación a Pro', async ({ page }) => {
  test.fixme(!ready('elegibilidad'), 'Espera E-16: elegibilidad en el resultado. Ver docs/runbooks/e2e-flujos-sprint4.md');
  const assessment = await evaluateAs(page, analystOf('FREE'));

  await expectEligibleProducts(page, assessment);
  expect(assessment.recomendaciones, 'sin ranking en el plan Gratis').toBeUndefined();
  await expect(page.getByRole('list', { name: 'Productos recomendados' })).toHaveCount(0);
  await expect(page.getByText(/disponibles? (en el plan|con) Pro/).first()).toBeVisible();
});

test('Pro: el resultado muestra los productos elegibles y el ranking con monto y plazo sugeridos', async ({ page }) => {
  test.fixme(!ready('elegibilidad'), 'Espera E-16: ranking en el resultado. Ver docs/runbooks/e2e-flujos-sprint4.md');
  const assessment = await evaluateAs(page, adminOf('PRO'));

  await expectEligibleProducts(page, assessment);
  expect(assessment.recomendaciones?.length, 'ranking del plan Pro').toBeGreaterThan(0);
  for (const recommendation of assessment.recomendaciones!) {
    expect(recommendation.plazoSugeridoMeses, 'cada recomendación trae plazo').toBeGreaterThan(0);
  }
  const ranking = page.getByRole('list', { name: 'Productos recomendados' }).getByRole('listitem');
  await expect(ranking).toHaveCount(assessment.recomendaciones!.length);
  await expect(ranking.first()).toContainText(/Bs/);
  await expect(ranking.first()).toContainText(`${assessment.recomendaciones![0].plazoSugeridoMeses} meses`);
  await expect(page.getByText(/disponibles? (en el plan|con) Pro/)).toHaveCount(0);
});
