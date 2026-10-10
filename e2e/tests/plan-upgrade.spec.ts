import { adminOf, changePlan, expect, login, openFromMenu, operator, ready, session, test } from './support';

// A-11: el cambio de plan es efectivo en ≤ 1 min sin volver a iniciar sesión, porque
// los entitlements se leen del servidor en cada petición y no viajan en el token.
const LIMIT_MS = 60_000;

test('Admin: de Gratis a Pro, las funciones Pro se habilitan en ≤ 1 min sin volver a iniciar sesión', async ({
  page,
  browser,
  apiErrors,
}) => {
  test.fixme(
    !ready('cambioDePlan'),
    'Espera A-11 (LOS-115): operador PLATFORM_ADMIN sembrado. Ver docs/runbooks/e2e-flujos-sprint3.md',
  );
  const admin = adminOf('FREE');
  await login(page, admin);
  // Desde aquí, la sesión del admin no debe volver a pasar por el login de Keycloak.
  const logins: string[] = [];
  page.on('framenavigated', (frame) => {
    if (frame === page.mainFrame() && frame.url().includes('/auth/realms/')) logins.push(frame.url());
  });

  await openFromMenu(page, 'Analítica');
  await expect(page.getByText('Analítica avanzada disponible con Pro')).toBeVisible();

  const operatorPage = await session(browser, operator(), apiErrors);
  try {
    await changePlan(operatorPage, admin.tenant, 'Pro');
    const changedAt = Date.now();

    // La misma pestaña, sin recargar: se vuelve a abrir Analítica hasta que el servidor la habilite.
    await expect(async () => {
      await openFromMenu(page, 'Resumen');
      await openFromMenu(page, 'Analítica');
      await expect(page.getByRole('heading', { name: 'Recomendaciones por producto' })).toBeVisible({ timeout: 3_000 });
    }).toPass({ timeout: LIMIT_MS, intervals: [1_000, 2_000, 5_000] });
    expect(Date.now() - changedAt, 'la función Pro se habilita en ≤ 1 min').toBeLessThanOrEqual(LIMIT_MS);
    await expect(page.getByText('Analítica avanzada disponible con Pro')).toHaveCount(0);

    // Otra función Pro en la misma sesión: los webhooks.
    await openFromMenu(page, 'Desarrolladores');
    await expect(page.getByRole('textbox', { name: 'URL del receptor' })).toBeVisible();
    expect(logins, 'el admin no volvió a iniciar sesión').toEqual([]);
  } finally {
    // Los demás escenarios usan este tenant como Gratis.
    await changePlan(operatorPage, admin.tenant, 'Gratis');
  }
});
