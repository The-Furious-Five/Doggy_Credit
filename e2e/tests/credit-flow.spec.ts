import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { apiRequest, apiResponse, connectProSources, login, seed } from './support.js';

for (const tenant of seed.tenants) {
  test(`${tenant.plan}: login → alta → consentimiento → evaluación → perfil → revocación → auditoría`, async ({ page }) => {
    const headers = await login(page, tenant);
    if (tenant.plan === 'PRO') await connectProSources(page);

    await page.goto('/app/evaluaciones/nueva');
    await page.getByRole('textbox', { name: 'Documento de identidad o NIT' }).fill(seed.document);
    await page.getByRole('textbox', { name: 'Actividad económica' }).fill('Comercio minorista (demo E2E)');
    for (const [label, value] of [
      ['Antigüedad de la actividad', '60'], ['Ingreso mensual declarado', '9000'],
      ['Monto solicitado', '5000'], ['Plazo', '12'],
    ]) {
      await page.getByRole('spinbutton', { name: label, exact: true }).fill(value);
    }
    await page.getByRole('combobox', { name: 'Destino del crédito' }).click();
    await page.getByRole('option', { name: 'Capital de trabajo', exact: true }).click();

    const applicantResponse = await apiResponse(page, '/solicitantes',
      () => page.getByRole('button', { name: 'Continuar', exact: true }).click(), 'POST');
    // Un Compose nuevo debe ejecutar el alta real, no reutilizar datos de otra ejecución.
    expect(applicantResponse.status()).toBe(201);
    const applicant = await applicantResponse.json();
    expect(applicant.documento).toBe(seed.document);
    expect(applicant.id).toBeTruthy();
    await expect(page.getByRole('heading', { name: 'Consentimiento', exact: true })).toBeVisible();
    const bureau = page.getByRole('checkbox', { name: 'Consultar burós de crédito' });
    await expect(bureau).not.toBeChecked();
    await page.getByRole('button', { name: 'Continuar', exact: true }).click();
    await expect(page.getByRole('status').filter({
      has: page.getByText('Falta el consentimiento obligatorio', { exact: true }),
    })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Consentimiento', exact: true })).toBeVisible();
    await expect(bureau).not.toBeChecked();
    await bureau.check();
    if (tenant.plan === 'PRO') {
      await page.getByRole('checkbox', { name: 'Consultar cuentas y movimientos bancarios' }).check();
      await page.getByRole('checkbox', { name: 'Consultar billeteras digitales' }).check();
    }
    const consentResponse = await apiResponse(page, '/consentimientos',
      () => page.getByRole('button', { name: 'Continuar', exact: true }).click(), 'POST');
    expect(consentResponse.status()).toBe(201);
    const consent = await consentResponse.json();
    expect(consent).toMatchObject({ titularDocumento: seed.document, estado: 'ACTIVO' });
    expect(consent.fuentes.sort()).toEqual(tenant.plan === 'PRO' ? ['BANCO', 'BURO', 'WALLET'] : ['BURO']);

    const evaluationResponse = await apiResponse(page, '/credit-assessments',
      () => page.getByRole('button', { name: 'Evaluar', exact: true }).click(), 'POST');
    expect(evaluationResponse.status()).toBe(201);
    expect(await evaluationResponse.request().headerValue('idempotency-key')).toMatch(/^[0-9a-f-]{36}$/i);
    const evaluation = await evaluationResponse.json();
    expect(evaluation.id).toBeTruthy();
    expect(evaluation.score).toBeGreaterThanOrEqual(0);
    expect(evaluation.score).toBeLessThanOrEqual(1000);
    expect(['A', 'B', 'C', 'D', 'E']).toContain(evaluation.banda);
    expect(['PREAPROBADO', 'REVISION_MANUAL', 'SOLICITAR_DATOS']).toContain(evaluation.decision);
    expect(evaluation.simulado).toBe(true);
    expect(evaluation.recomendacionesDisponibles).toBe(tenant.plan === 'PRO');
    expect(evaluation.modelVersion).toMatch(tenant.plan === 'PRO' ? /^scoring-/ : /^basic-/);
    await expect(page.locator('ds-stat').filter({ has: page.getByText('Score', { exact: true }) }))
      .toContainText(String(evaluation.score));

    const profileResponse = await apiResponse(page, `/solicitantes/${applicant.id}`,
      () => page.getByRole('link', { name: 'Ver perfil completo' }).click());
    await expect(page).toHaveURL(new RegExp(`/app/clientes/${applicant.id}$`));
    expect(profileResponse.status()).toBe(200);
    const detail = await profileResponse.json();
    expect(detail.perfil, 'La evaluación debe persistir el Perfil 360 antes de responder').toBeTruthy();
    expect(detail.perfil.hash).toMatch(/^[a-f0-9]{64}$/i);
    expect(detail.perfil.fuentes.find((source: { fuente: string; estado: string }) => source.fuente === 'BURO'))
      .toMatchObject({ estado: 'COMPLETA' });
    for (const source of ['BANCO', 'WALLET']) {
      expect(detail.perfil.fuentes.find((entry: { fuente: string }) => entry.fuente === source))
        .toMatchObject({ estado: tenant.plan === 'PRO' ? 'COMPLETA' : 'NO_CONSULTADA' });
    }
    expect(detail.perfil.origenIngreso).toBe(tenant.plan === 'PRO' ? 'BANCO' : 'DECLARADO');
    await expect(page.getByRole('heading', { name: seed.document, exact: true })).toBeVisible();
    await expect(page.getByText('Consentimiento vigente', { exact: true })).toBeVisible();
    await expect(page.locator('ds-stat').filter({ has: page.getByText('Capacidad de pago', { exact: true }) }))
      .not.toContainText('—');
    await page.getByRole('tab', { name: 'Score', exact: true }).click();
    await expect(page.getByRole('table', { name: 'Historial de evaluaciones' })
      .getByRole('row').filter({ has: page.getByRole('cell', { name: String(evaluation.score), exact: true }) }))
      .toContainText(evaluation.banda);

    await page.getByRole('tab', { name: 'Recomendaciones', exact: true }).click();
    const recommendations = page.getByRole('tabpanel', { name: 'Recomendaciones', exact: true });
    if (tenant.plan === 'PRO') {
      // C-06 publica el snapshot real; el perfil debe mostrar el mismo producto y monto.
      expect.soft(Array.isArray(evaluation.recomendaciones), 'La evaluación debe publicar recomendaciones[]').toBe(true);
      expect.soft(evaluation.recomendaciones?.length ?? 0, 'El buen pagador debe recibir un producto Pro').toBeGreaterThan(0);
      await expect.soft(recommendations.getByRole('heading', { name: 'Sin recomendaciones', exact: true })).not.toBeVisible();
      const labels: Record<string, string> = { MICROCREDITO: 'Microcrédito', PYME: 'Crédito PYME', VERDE: 'Crédito verde' };
      for (const product of evaluation.recomendaciones ?? []) {
        expect.soft(labels[product.tipo]).toBeTruthy();
        expect.soft(Number(product.montoRecomendado)).toBeGreaterThan(0);
        if (labels[product.tipo]) {
          const card = recommendations.locator('ds-card').filter({
            has: page.getByRole('heading', { name: labels[product.tipo], exact: true }),
          });
          await expect.soft(card).toBeVisible();
          await expect.soft(card).toContainText(new Intl.NumberFormat('es-BO', { maximumFractionDigits: 2 })
            .format(Number(product.montoRecomendado)));
        }
      }
    } else {
      expect.soft(evaluation.recomendaciones).toEqual([]);
      await expect.soft(recommendations.getByRole('status').filter({
        has: page.getByText('Recomendaciones disponibles con Pro', { exact: true }),
      })).toBeVisible();
    }

    await page.getByRole('button', { name: 'Más acciones' }).click();
    await page.getByRole('menuitem', { name: 'Revocar consentimiento' }).click();
    const revokedResponse = await apiResponse(page, `/consentimientos/${consent.id}/revocar`,
      () => page.getByRole('dialog', { name: 'Revocar consentimiento' })
        .getByRole('button', { name: 'Revocar consentimiento', exact: true }).click(), 'POST');
    expect(revokedResponse.status()).toBe(200);
    expect(await revokedResponse.json()).toMatchObject({ id: consent.id, estado: 'REVOCADO' });
    const correlationId = revokedResponse.headers()['x-correlation-id'];
    expect(correlationId).toBeTruthy();
    await expect(page.getByText('Sin consentimiento vigente', { exact: true })).toBeVisible();

    // La revocación debe impedir consultar fuentes, incluso llamando directamente a la API.
    const denied = await apiRequest(page, '/api/v1/credit-assessments', {
      method: 'POST',
      headers: { ...headers, 'Idempotency-Key': randomUUID() },
      data: { titularDocumento: seed.document, consentId: consent.id, proposito: 'Capital de trabajo', montoSolicitado: '5000', plazoMeses: 12 },
    });
    expect(denied.status()).toBe(422);
    expect(await denied.json()).toMatchObject({ code: 'CONSENT_REQUIRED' });

    const auditResponse = await apiResponse(page, '/auditoria', async () => {
      await page.goto('/app/configuracion#seguridad');
      await page.getByRole('tab', { name: 'Seguridad', exact: true }).click();
    });
    expect(auditResponse.status()).toBe(200);
    const entries = (await auditResponse.json()).items;
    expect(entries).toEqual(expect.arrayContaining([expect.objectContaining({
      accion: 'CONSENT_REVOKED', correlationId, tipoRecurso: 'CONSENT',
    })]));
    await expect(page.getByRole('table', { name: 'Registros de auditoría, del más reciente al más antiguo' })
      .getByRole('row').filter({ hasText: correlationId })).toContainText('CONSENT_REVOKED');
  });
}
