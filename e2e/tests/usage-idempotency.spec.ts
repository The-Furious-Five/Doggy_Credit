import type { Page } from '@playwright/test';
import { adminOf, captureApi, evaluate, expect, login, openFromMenu, test } from './support';

// RF-ANA-01 (B-12): una evaluación suma exactamente una al consumo del mes y repetirla
// con la misma Idempotency-Key no vuelve a sumar (devuelve la evaluación original).
// Plan Pro: sin cuota mensual, así el escenario no compite por la del tenant Gratis.
const DOCUMENT = 'CI-DEMO-0001001';

type Captured = Awaited<ReturnType<typeof captureApi>>;

/** Lo que muestra Configuración › Plan, comprobado contra GET /tenant/actual/uso de esa misma visita. */
async function usageShown(page: Page, api: Captured): Promise<number> {
  await openFromMenu(page, 'Resumen');
  const seen = api.length;
  await openFromMenu(page, 'Configuración');
  await page.getByRole('tab', { name: 'Plan' }).click();
  const text = page.getByText(/^\d+ (de \d+ )?evaluaciones utilizadas este mes/);
  await expect(text).toBeVisible();
  const shown = Number((await text.innerText()).match(/^\d+/)![0]);
  // captureApi lee el cuerpo antes de entregarlo al navegador: Chromium puede descartarlo después.
  await expect.poll(() => api.length, { message: 'Configuración consulta el uso' }).toBeGreaterThan(seen);
  const { evaluacionesMes } = api[api.length - 1].body as { evaluacionesMes: number };
  expect(shown, 'el contador de la interfaz es el consumo registrado').toBe(evaluacionesMes);
  return shown;
}

test('Admin: una evaluación suma 1 al consumo y repetirla con la misma Idempotency-Key no suma', async ({ page }) => {
  await login(page, adminOf('PRO'));
  const usage = await captureApi(page, (status, method, url) =>
    status === 200 && method === 'GET' && new URL(url).pathname.endsWith('/api/v1/tenant/actual/uso'),
  );
  const before = await usageShown(page, usage);

  // La SPA genera la Idempotency-Key; se guarda la petición y la respuesta tal como viajaron.
  let original: { url: string; headers: Record<string, string>; body: string; id: string } | undefined;
  await page.route('**/api/v1/credit-assessments', async (route) => {
    const request = route.request();
    const upstream = await route.fetch();
    if (request.method() === 'POST' && upstream.ok()) {
      const { id } = (await upstream.json()) as { id: string };
      original = { url: request.url(), headers: await request.allHeaders(), body: request.postData() ?? '', id };
    }
    await route.fulfill({ response: upstream });
  });

  await evaluate(page, DOCUMENT);
  expect(original, 'la consola creó la evaluación').toBeDefined();
  await expect.poll(() => usageShown(page, usage), { message: 'el consumo sube exactamente en 1' }).toBe(before + 1);

  // Misma petición, misma Idempotency-Key y mismo token: la API devuelve la evaluación original.
  const replay = await page.request.post(original!.url, {
    headers: {
      authorization: original!.headers['authorization'],
      'content-type': 'application/json',
      'idempotency-key': original!.headers['idempotency-key'],
    },
    data: original!.body,
  });
  expect(replay.ok(), `reintento idempotente: ${replay.status()}`).toBe(true);
  expect(((await replay.json()) as { id: string }).id, 'devuelve la misma evaluación').toBe(original!.id);
  expect(await usageShown(page, usage), 'el reintento no suma consumo').toBe(before + 1);
});
