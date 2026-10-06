import { createServer, type Server } from 'node:http';
import { expect, test } from '@playwright/test';
import { apiResponse } from '../tests/api-response.js';

// Reproduce el fallo de protocolo con red y navegación reales, sin Compose.
// El servidor de esta regresión es sintético; los cinco escenarios de negocio
// siguen consultando la API real de Compose.
const cases = [
  { path: '/tenant/actual', method: 'GET', status: 200, payload: { id: 'free', plan: 'FREE' } },
  { path: '/analytics/resumen', method: 'GET', status: 200, payload: { plan: 'PRO', analiticaAvanzada: true } },
  { path: '/auditoria', method: 'GET', status: 200, payload: { items: [{ accion: 'CONSENT_REVOKED' }] } },
  { path: '/solicitantes', method: 'POST', status: 201, payload: { id: 'solicitante', documento: 'SINTETICO' } },
  { path: '/webhooks', method: 'GET', status: 403, payload: { code: 'FEATURE_NOT_IN_PLAN' } },
];
let server: Server;
let origin: string;
const calls: { path: string; method: string; authorized: boolean; body: string }[] = [];
const pending = new Set<NodeJS.Timeout>();

test.beforeAll(async () => {
  server = createServer(async (request, response) => {
    const url = new URL(request.url!, 'http://localhost');
    const scenario = cases.find(item => url.pathname === `/api/v1${item.path}`);
    if (scenario) {
      const chunks = [];
      for await (const chunk of request) chunks.push(Buffer.from(chunk));
      calls.push({ path: scenario.path, method: request.method!, authorized: Boolean(request.headers.authorization),
        body: Buffer.concat(chunks).toString('utf8') });
      response.writeHead(scenario.status, { 'content-type': 'application/json', 'x-correlation-id': 'regression-id' });
      const body = JSON.stringify(scenario.payload);
      response.write(body.slice(0, 1));
      // El navegador puede navegar al recibir cabeceras, antes del resto del JSON.
      const timer = setTimeout(() => { pending.delete(timer); response.end(body.slice(1)); }, 100);
      pending.add(timer);
    } else {
      response.writeHead(200, { 'content-type': 'text/html' });
      response.end(`<button>Enviar</button><script>
        const query = new URLSearchParams(location.search);
        document.querySelector('button').onclick = () => fetch('/api/v1' + query.get('path'), {
          method: query.get('method'),
          headers: { Authorization: 'Bearer regression-fixture', 'Content-Type': 'application/json',
            'Idempotency-Key': 'regression-key' },
          ...(query.get('method') === 'POST' ? { body: JSON.stringify({ documento: 'SINTETICO' }) } : {})
        }).then(() => location.replace('/navegada'));
      </script>`);
    }
  });
  await new Promise<void>((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('El servidor no tiene un puerto TCP');
  origin = `http://127.0.0.1:${address.port}`;
});

test.afterAll(async () => {
  for (const timer of pending) clearTimeout(timer);
  pending.clear();
  server?.closeAllConnections();
  if (server?.listening) await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
});

for (const scenario of cases) {
  test(`${scenario.method} ${scenario.path}: conserva la respuesta real después de cambiar de documento`, async ({ page }) => {
    const query = new URLSearchParams({ path: scenario.path, method: scenario.method });
    await page.goto(`${origin}/?${query}`);
    const captured = await apiResponse(page, scenario.path, () => page.getByRole('button', { name: 'Enviar' }).click(), scenario.method);
    await expect(page).toHaveURL(`${origin}/navegada`);
    expect(captured.status()).toBe(scenario.status);
    expect(captured.headers()['x-correlation-id']).toBe('regression-id');
    expect(await captured.json()).toEqual(scenario.payload);
    expect(calls.filter(call => call.path === scenario.path)).toEqual([{
      path: scenario.path, method: scenario.method, authorized: true,
      body: scenario.method === 'POST' ? '{"documento":"SINTETICO"}' : '',
    }]);
  });
}
