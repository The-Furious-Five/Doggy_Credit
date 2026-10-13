import { adminOf, analystOf, evaluate, expect, login, openFromMenu, ready, session, test, toast } from './support';

// Titular sintético «moroso» del tenant Gratis (seed/personas-sinteticas.json): ninguna
// política lo preaprueba, así que la evaluación queda en revisión manual (C-05).
const DOCUMENT = 'CI-DEMO-0001002';
const JUSTIFICATION = 'Mora regularizada hace 3 meses; ingresos verificados con boletas de pago.';

test('Analista: resuelve un caso en revisión manual con override justificado y queda en la auditoría', async ({
  page,
  browser,
  apiErrors,
}) => {
  test.fixme(!ready('bandeja'), 'Espera C-10 (LOS-111): backend de la bandeja. Ver docs/runbooks/e2e-flujos-sprint3.md');

  // 1. Una evaluación que las políticas dejan para una persona.
  await login(page, analystOf('FREE'));
  const { applicantId, decision } = await evaluate(page, DOCUMENT);
  expect(decision, 'el titular moroso queda en revisión manual').toContain('Revisión manual');

  // 2. El caso aparece en la bandeja (identificado por su solicitante: el documento se enmascara al analista).
  await openFromMenu(page, 'Revisión manual');
  await expect(page.getByRole('heading', { name: 'Revisión manual', level: 1 })).toBeVisible();
  const cases = page.locator('tbody tr').filter({ has: page.locator(`a[href="/app/clientes/${applicantId}"]`) });
  await expect(cases.first()).toBeVisible();
  const pending = await cases.count();

  // 3. Sin justificación no se envía nada.
  const resolutions: string[] = [];
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().includes('/resolucion')) resolutions.push(request.url());
  });
  await cases.first().getByRole('button', { name: /^Resolver caso de / }).click();
  const dialog = page.getByRole('dialog', { name: 'Resolver caso' });
  await dialog.getByRole('radio', { name: 'Aprobar' }).check();
  await dialog.getByRole('combobox', { name: 'Producto que se otorga' }).click();
  const product = page.getByRole('option').first();
  const productName = (await product.innerText()).split('\n')[0].trim();
  await product.click();
  await dialog.getByRole('button', { name: 'Resolver caso' }).click();
  await expect(dialog.getByText('La justificación es obligatoria.')).toBeVisible();
  await expect(dialog.getByRole('textbox', { name: 'Justificación' })).toBeFocused();
  expect(resolutions, 'no se envía una resolución sin justificación').toEqual([]);

  // 4. Override justificado: la API lo acepta y el caso sale de la bandeja.
  // Capturar el cuerpo del servidor antes de entregarlo al navegador. Chromium puede
  // descartar su buffer después de que la interfaz procese la respuesta y cierre el diálogo.
  let resolutionBody: unknown;
  await page.route('**/api/v1/revision-manual/*/resolucion', async (route) => {
    const upstream = await route.fetch();
    resolutionBody = await upstream.json();
    await route.fulfill({ response: upstream });
  });
  await dialog.getByRole('textbox', { name: 'Justificación' }).fill(JUSTIFICATION);
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'POST' && r.url().includes('/resolucion')),
    dialog.getByRole('button', { name: 'Resolver caso' }).click(),
  ]);
  expect(response.status()).toBe(200);
  expect(resolutionBody).toMatchObject({ decision: 'APROBADO', justificacion: JUSTIFICATION });
  const correlationId = response.headers()['x-correlation-id'];
  expect(correlationId, 'la resolución devuelve su X-Correlation-Id').toBeTruthy();
  await expect(toast(page, `Caso aprobado con ${productName}.`)).toBeVisible();
  await expect(dialog).toBeHidden();
  await expect(cases).toHaveCount(pending - 1);

  // 5. El override queda en la bitácora con el mismo correlation-id (lo ve TENANT_ADMIN).
  const admin = await session(browser, adminOf('FREE'), apiErrors);
  await admin.goto('/app/configuracion#seguridad');
  await expect(admin.getByRole('heading', { name: 'Bitácora de auditoría' })).toBeVisible();
  await expect(admin.getByRole('table').getByRole('row').filter({ hasText: correlationId }).first()).toBeVisible();
});
