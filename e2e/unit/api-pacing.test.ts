import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { APIResponse, Page, Route } from '@playwright/test';
import { installApiPacing, RequestPacer, waitForApiBudget } from '../tests/api-pacing.js';
import { apiRequest } from '../tests/api-response.js';

test('una ráfaga concurrente de FREE nunca consume 30 peticiones en menos de un minuto', async () => {
  let now = 0;
  const starts: number[] = [];
  const pacer = new RequestPacer(2_100, () => now, async ms => { now += ms; });
  await Promise.all(Array.from({ length: 65 }, () => pacer.wait().then(() => { starts.push(now); })));
  assert.equal(starts[0], 0);
  for (let i = 1; i < starts.length; i++) assert.ok(starts[i] - starts[i - 1] >= 2_100);
  for (const start of starts) assert.ok(starts.filter(time => time >= start && time < start + 60_000).length <= 29);
});

test('el tiempo ocioso libera el siguiente turno sin añadir una pausa artificial', async () => {
  let now = 0;
  const sleeps: number[] = [];
  const pacer = new RequestPacer(2_100, () => now, async ms => { sleeps.push(ms); now += ms; });
  await pacer.wait();
  now = 80_000;
  await pacer.wait();
  assert.deepEqual(sleeps, []);
});

test('una demora del proceso no libera juntas las peticiones pendientes', async () => {
  let now = 0;
  const starts: number[] = [];
  const pacer = new RequestPacer(2_100, () => now, async ms => { now += ms + 60_000; });
  await Promise.all(Array.from({ length: 3 }, () => pacer.wait().then(() => { starts.push(now); })));
  assert.deepEqual(starts, [0, 62_100, 124_200]);
});

test('PRO no espera y un fallo en la espera de FREE se propaga', async () => {
  const pro = new RequestPacer(0, () => 0, async () => { assert.fail('PRO no debe esperar'); });
  await Promise.all([pro.wait(), pro.wait()]);
  const free = new RequestPacer(2_100, () => 0, async () => { throw new Error('reloj interrumpido'); });
  await free.wait();
  await assert.rejects(free.wait(), /reloj interrumpido/);
});

function pageAt(origin = 'https://localhost') {
  let matches!: (url: URL) => boolean;
  let handler!: (route: Route) => Promise<void>;
  let registrations = 0;
  let fallbacks = 0;
  let closed = false;
  let cancelled = false;
  const page = {
    url: () => `${origin}/acceso`,
    isClosed: () => closed,
    route: async (predicate: typeof matches, callback: typeof handler) => {
      matches = predicate; handler = callback; registrations++;
    },
  } as unknown as Page;
  return { page, get registrations() { return registrations; }, get fallbacks() { return fallbacks; },
    matches: (url: string) => matches(new URL(url)),
    close: () => { closed = true; },
    cancel: () => { cancelled = true; },
    dispatch: () => handler({
      request: () => ({ failure: () => cancelled ? { errorText: 'net::ERR_ABORTED' } : null }),
      fallback: async () => { fallbacks++; },
    } as unknown as Route) };
}

test('SPA y consultas directas comparten la cola entre contextos del mismo tenant', async t => {
  const turns: RequestPacer[] = [];
  t.mock.method(RequestPacer.prototype, 'wait', function (this: RequestPacer) {
    turns.push(this);
    return Promise.resolve();
  });
  const first = pageAt();
  const second = pageAt();
  const other = pageAt();
  await installApiPacing(first.page, { id: 'shared-free', plan: 'FREE' });
  await installApiPacing(first.page, { id: 'shared-free', plan: 'FREE' });
  await installApiPacing(second.page, { id: 'shared-free', plan: 'FREE' });
  await installApiPacing(other.page, { id: 'independent-pro', plan: 'PRO' });
  assert.equal(first.registrations, 1);
  assert.equal(first.matches('https://localhost/api/v1/tenant/actual'), true);
  assert.equal(first.matches('https://foreign.example/api/v1/tenant/actual'), false);
  assert.equal(first.matches('https://localhost/auth/realms/doggycredit'), false);
  await first.dispatch();
  await waitForApiBudget(second.page);
  await other.dispatch();
  assert.equal(first.fallbacks, 1);
  assert.equal(turns.length, 3);
  assert.equal(turns[0], turns[1]);
  assert.notEqual(turns[0], turns[2]);
});

test('una navegación o el cierre del contexto no reenvía peticiones de fondo canceladas', async t => {
  let release!: () => void;
  t.mock.method(RequestPacer.prototype, 'wait', () => new Promise<void>(resolve => { release = resolve; }));
  for (const cancellation of ['navigate', 'close']) {
    const page = pageAt();
    await installApiPacing(page.page, { id: `cancelled-${cancellation}`, plan: 'FREE' });
    const pending = page.dispatch();
    if (cancellation === 'navigate') page.cancel();
    else page.close();
    release();
    await pending;
    assert.equal(page.fallbacks, 0);
  }
});

test('la API directa conserva un 429 y envía el POST exactamente una vez', async t => {
  let turns = 0;
  t.mock.method(RequestPacer.prototype, 'wait', async () => { turns++; });
  const { page } = pageAt();
  await installApiPacing(page, { id: 'direct-free', plan: 'FREE' });
  const options = { method: 'POST', data: { consentId: 'fixture-consent' }, headers: { 'Idempotency-Key': 'fixture-key' } };
  const real = { status: () => 429, json: async () => ({ code: 'RATE_LIMITED' }) } as unknown as APIResponse;
  let fetches = 0;
  Object.assign(page, { request: { fetch: async (path: string, sent: unknown) => {
    fetches++;
    assert.equal(path, '/api/v1/credit-assessments');
    assert.deepEqual(sent, { ...options, maxRedirects: 0, maxRetries: 0 });
    return real;
  } } });
  const response = await apiRequest(page, '/api/v1/credit-assessments', options);
  assert.equal(response.status(), 429);
  assert.deepEqual(await response.json(), { code: 'RATE_LIMITED' });
  assert.equal(fetches, 1);
  assert.equal(turns, 1);
});

test('un error de transporte no se reintenta ni expone credenciales', async () => {
  const { page } = pageAt();
  let fetches = 0;
  Object.assign(page, { request: { fetch: async () => {
    fetches++;
    throw new Error('Authorization: Bearer fixture');
  } } });
  await assert.rejects(apiRequest(page, '/api/v1/tenant/actual'), error => {
    assert.ok(error instanceof Error);
    assert.match(error.message, /No se pudo obtener la respuesta real/);
    assert.doesNotMatch(error.message, /Bearer|fixture/);
    return true;
  });
  assert.equal(fetches, 1);
});
