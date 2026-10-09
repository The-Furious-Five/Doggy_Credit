import { adminOf, connect, expect, login, openFromMenu, test, type Plan } from './support';

// Titular sintético «buen pagador» presente en buró, banco y billetera (seed/personas-sinteticas.json).
const DOCUMENT = 'CI-DEMO-0001001';

/** Lo que cambia entre planes en el recorrido: fuentes que se pueden activar y origen del ingreso. */
const EXPECTED: Record<Plan, { bank: string; wallet: string; income: string }> = {
  // El buró ocupa la única integración del plan Gratis: banco y billetera no se consultan.
  FREE: { bank: 'No consultada', wallet: 'No consultada', income: 'Declarado por el cliente' },
  PRO: { bank: 'Completa', wallet: 'Completa', income: 'Promedio de 6 meses en el banco' },
};

for (const plan of ['FREE', 'PRO'] as const satisfies readonly Plan[]) {
  test(`${plan}: alta, consentimiento, evaluación, perfil, revocación y auditoría`, async ({ page }) => {
    const expected = EXPECTED[plan];
    await login(page, adminOf(plan));

    if (plan === 'PRO') {
      // El plan Pro admite sumar fuentes al buró obligatorio.
      await openFromMenu(page, 'Integraciones');
      await connect(page, 'Core bancario');
      await connect(page, 'Billetera móvil');
    }

    // 1. Nueva evaluación: solicitante (si el documento ya existe, la vista lo reutiliza).
    await openFromMenu(page, 'Nueva evaluación');
    await expect(page.getByRole('heading', { name: 'Nueva evaluación', level: 1 })).toBeVisible();
    await page.getByRole('textbox', { name: 'Documento de identidad o NIT' }).fill(DOCUMENT);
    await page.getByRole('textbox', { name: 'Actividad económica' }).fill('Comercio minorista');
    await page.getByRole('spinbutton', { name: 'Ingreso mensual declarado' }).fill('6500');
    await page.getByRole('spinbutton', { name: 'Monto solicitado' }).fill('15000');
    await page.getByRole('combobox', { name: 'Destino del crédito' }).click();
    await page.getByRole('option', { name: 'Capital de trabajo' }).click();
    await page.getByRole('button', { name: 'Continuar' }).click();

    // 2. Consentimiento: sin el buró no se puede seguir.
    await expect(page.getByRole('heading', { name: 'Consentimiento', level: 2 })).toBeVisible();
    await page.getByRole('button', { name: 'Continuar' }).click();
    await expect(page.getByText('Falta el consentimiento obligatorio')).toBeVisible();
    await page.getByRole('checkbox', { name: 'Consultar burós de crédito' }).check();
    await page.getByRole('checkbox', { name: 'Consultar cuentas y movimientos bancarios' }).check();
    await page.getByRole('checkbox', { name: 'Consultar billeteras digitales' }).check();
    await page.getByRole('button', { name: 'Continuar' }).click();

    // 3. Fuentes del tenant y evaluación.
    await expect(page.getByRole('heading', { name: 'Fuentes', level: 2 })).toBeVisible();
    await expect(page.getByRole('listitem').filter({ hasText: 'Buró de crédito' })).toContainText('Conectado');
    await page.getByRole('button', { name: 'Evaluar' }).click();

    // 4. Resultado: score, capacidad de pago (del Perfil 360), decisión y razones.
    await expect(page.getByRole('heading', { name: 'Resultado', level: 2 })).toBeVisible();
    const scoreStat = page.locator('ds-stat').filter({ hasText: 'Score' });
    await expect(scoreStat).toContainText(/Banda [A-E]/);
    const score = (await scoreStat.innerText()).match(/\d{3}/)?.[0];
    expect(score, 'score de 3 dígitos en el resultado').toBeTruthy();
    await expect(page.locator('ds-stat').filter({ hasText: 'Capacidad de pago' })).toContainText(/Bs\s?[\d.]+/);
    await expect(page.locator('ds-stat').filter({ hasText: 'Decisión' })).toContainText(
      /Preaprobado|Revisión manual|Solicitar datos/,
    );
    await expect(page.getByRole('heading', { name: 'Principales razones' })).toBeVisible();

    // 5. Perfil del cliente: Perfil 360, score y fuentes.
    await page.getByRole('link', { name: 'Ver perfil completo' }).click();
    await expect(page).toHaveURL(/\/app\/clientes\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('heading', { name: DOCUMENT, level: 1 })).toBeVisible();
    await expect(page.getByText('Consentimiento vigente', { exact: true })).toBeVisible();
    const tabs = page.getByRole('tablist', { name: 'Perfil del cliente' });

    const summary = page.getByRole('tabpanel', { name: 'Resumen' });
    await expect(summary.locator('ds-stat').filter({ hasText: 'Capacidad de pago' })).toContainText(/Bs\s?[\d.]+/);
    await expect(summary.locator('ds-stat').filter({ hasText: 'Ingreso mensual' })).toContainText(expected.income);

    await tabs.getByRole('tab', { name: 'Score' }).click();
    const history = page.getByRole('tabpanel', { name: 'Score' }).getByRole('row');
    await expect(history.nth(1)).toContainText(score!);

    await tabs.getByRole('tab', { name: 'Fuentes' }).click();
    const sources = page.getByRole('tabpanel', { name: 'Fuentes' });
    await expect(sources.getByRole('row', { name: /Buró de crédito/ })).toContainText('Completa');
    await expect(sources.getByRole('row', { name: /Core bancario/ })).toContainText(expected.bank);
    await expect(sources.getByRole('row', { name: /Billetera móvil/ })).toContainText(expected.wallet);

    // 6. Revocar el consentimiento.
    await page.getByRole('button', { name: 'Más acciones' }).click();
    await page.getByRole('menuitem', { name: 'Revocar consentimiento' }).click();
    const dialog = page.getByRole('dialog', { name: 'Revocar consentimiento' });
    await dialog.getByRole('button', { name: 'Revocar consentimiento' }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByText('Sin consentimiento vigente', { exact: true })).toBeVisible();

    // 7. Auditoría en Configuración › Seguridad.
    await page.goto('/app/configuracion#seguridad');
    await expect(page.getByRole('heading', { name: 'Bitácora de auditoría' })).toBeVisible();
    const audit = page.getByRole('table');
    for (const action of ['ASSESSMENT_CREATED', 'FINANCIAL_PROFILE_CREATED', 'CONSENT_REVOKED']) {
      await expect(audit.getByRole('rowheader', { name: action, exact: true }).first()).toBeVisible();
    }
  });
}
