import {
  analystOf,
  captureApi,
  expect,
  keycloakLogins,
  login,
  openFromMenu,
  operator,
  session,
  setTenantActive,
  test,
} from './support';

// RF-TEN-04 (E-14 / B-13): la suspensión y la reactivación son efectivas en ≤ 1 min con
// el mismo token, porque el estado del tenant se lee del servidor en cada petición.
const LIMIT_MS = 60_000;
const RETRY = { timeout: LIMIT_MS, intervals: [1_000, 2_000, 5_000] };

test('Operador: suspende la institución, su analista recibe 403 TENANT_SUSPENDED y al reactivarla vuelve a operar sin iniciar sesión', async ({
  page,
  browser,
  apiErrors,
}) => {
  // El tenant Gratis del analista; el operador vive en otro tenant, así que no se bloquea a sí mismo.
  // Los escenarios corren en serie (workers: 1) y el finally deja la institución activa.
  const analyst = analystOf('FREE');
  await login(page, analyst);
  const logins = keycloakLogins(page);
  const rejected = await captureApi(page, (status) => status === 403);

  const operatorPage = await session(browser, operator(), apiErrors);
  try {
    // 1. El operador suspende la institución.
    await setTenantActive(operatorPage, analyst.tenant, false);

    // 2. La sesión ya abierta del analista: la API rechaza con 403 TENANT_SUSPENDED (RFC 9457)
    //    y la consola le avisa que su institución está suspendida.
    await expect(async () => {
      await openFromMenu(page, 'Resumen');
      await openFromMenu(page, 'Clientes');
      expect(rejected.map((r) => r.body)).toContainEqual(
        expect.objectContaining({ status: 403, code: 'TENANT_SUSPENDED', correlationId: expect.any(String) }),
      );
    }).toPass(RETRY);
    await expect(
      page.getByRole('status').or(page.getByRole('alert')).filter({ hasText: /suspendid/i }).first(),
    ).toBeVisible();

    // 3. El operador la reactiva.
    await setTenantActive(operatorPage, analyst.tenant, true);

    // 4. El analista vuelve a operar con la misma sesión: la lista de clientes carga sin errores.
    rejected.length = 0;
    await expect(async () => {
      const loaded = page.waitForResponse(
        (r) => new URL(r.url()).pathname.endsWith('/api/v1/solicitantes') && r.status() === 200,
        { timeout: 5_000 },
      );
      await openFromMenu(page, 'Resumen');
      await openFromMenu(page, 'Clientes');
      await loaded;
    }).toPass(RETRY);
    await expect(page.getByRole('heading', { name: 'Clientes', level: 1 })).toBeVisible();
    rejected.length = 0;
    await openFromMenu(page, 'Resumen');
    await openFromMenu(page, 'Analítica');
    await expect(page.getByRole('heading', { name: 'Analítica', level: 1 })).toBeVisible();
    expect(rejected, 'sin rechazos después de reactivar').toEqual([]);
    expect(logins, 'el analista no volvió a iniciar sesión').toEqual([]);
  } finally {
    // Los demás escenarios necesitan la institución activa.
    await setTenantActive(operatorPage, analyst.tenant, true);
  }
});
