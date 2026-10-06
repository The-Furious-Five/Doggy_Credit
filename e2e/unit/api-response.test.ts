import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { APIResponse, Page, Route } from '@playwright/test';
import { apiResponse } from '../tests/api-response.js';

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(ok => { resolve = ok; });
  return { promise, resolve };
}

function routing() {
  let match: (url: URL) => boolean;
  let handle: (route: Route) => Promise<void>;
  let active = false;
  let registration: Promise<void> = Promise.resolve();
  let closed = false;
  let removals = 0;
  const page = {
    url: () => 'https://localhost/app/resumen',
    isClosed: () => closed,
    route: async (predicate: typeof match, handler: typeof handle) => {
      await registration;
      match = predicate; handle = handler; active = true;
    },
    unroute: async (predicate: typeof match, handler: typeof handle) => {
      assert.equal(predicate, match);
      assert.equal(handler, handle);
      active = false; removals++;
    },
  } as unknown as Page;
  return {
    page,
    get active() { return active; },
    get removals() { return removals; },
    delayRegistration: (wait: Promise<void>) => { registration = wait; },
    close: () => { closed = true; active = false; },
    emit: async (route: Route) => {
      assert.equal(active, true, 'La captura debe estar registrada antes de la acción');
      if (match(new URL(route.request().url()))) await handle(route);
    },
  };
}

function resource(path: string, method = 'GET', status = 200, content = '{"plan":"FREE","items":[]}') {
  let disposed = false;
  let body: Promise<Buffer> = Promise.resolve(Buffer.from(content));
  let fetchError: Error | undefined;
  const state = { fetches: 0, fulfilled: 0, fallbacks: 0, aborted: 0, payload: Buffer.alloc(0) };
  const upstream = {
    url: () => `https://localhost/api/v1${path}`,
    status: () => status,
    headers: () => ({ 'content-type': 'application/json', 'x-correlation-id': 'correlation-test' }),
    body: async () => {
      assert.equal(disposed, false, 'No se puede consultar la APIResponse después de dispose');
      return body;
    },
    dispose: async () => { disposed = true; },
  } as unknown as APIResponse;
  const request = {
    url: () => upstream.url(), method: () => method,
    allHeaders: async () => ({ authorization: 'Bearer fixture', 'idempotency-key': 'idempotency-fixture' }),
  };
  const route = {
    request: () => request,
    fetch: async (options: unknown) => {
      state.fetches++;
      assert.deepEqual(options, { maxRedirects: 0, maxRetries: 0, timeout: 30_000 });
      if (fetchError) throw fetchError;
      return upstream;
    },
    fulfill: async (options: { response: APIResponse; body: Buffer }) => {
      assert.equal(options.response, upstream);
      state.payload = Buffer.from(options.body); state.fulfilled++;
    },
    fallback: async () => { state.fallbacks++; },
    abort: async () => { state.aborted++; },
  } as unknown as Route;
  return { route, state, upstream, delayBody: (wait: Promise<Buffer>) => { body = wait; },
    fail: (error: Error) => { fetchError = error; } };
}

for (const path of ['/tenant/actual', '/analytics/resumen', '/auditoria']) {
  test(`${path}: conserva el JSON después de la navegación y dispose, sin usar el cuerpo de Chromium`, async () => {
    const events = routing();
    const real = resource(path);
    const response = await apiResponse(events.page, path, async () => { await events.emit(real.route); });
    await assert.rejects(real.upstream.body()); // Ya se liberó la APIResponse original.
    assert.deepEqual(await response.json(), { plan: 'FREE', items: [] });
    assert.equal(response.status(), 200);
    assert.equal(response.headers()['x-correlation-id'], 'correlation-test');
    assert.equal(await response.request().headerValue('Authorization'), 'Bearer fixture');
    assert.equal(real.state.fetches, 1);
    assert.equal(real.state.fulfilled, 1);
    assert.equal(events.active, false);
    assert.equal(events.removals, 1);
  });
}

test('no entrega las cabeceras al navegador hasta conservar todo el cuerpo', async () => {
  const events = routing();
  const real = resource('/tenant/actual');
  const body = deferred<Buffer>();
  const started = deferred<void>();
  real.delayBody(body.promise);
  const capture = apiResponse(events.page, '/tenant/actual', async () => {
    const arrival = events.emit(real.route);
    started.resolve();
    await arrival;
  });
  await started.promise;
  assert.equal(real.state.fulfilled, 0);
  body.resolve(Buffer.from('{"plan":"PRO"}'));
  assert.deepEqual(await (await capture).json(), { plan: 'PRO' });
  assert.equal(real.state.fulfilled, 1);
});

test('espera el registro de la captura antes de disparar la acción', async () => {
  const events = routing();
  const registration = deferred<void>();
  events.delayRegistration(registration.promise);
  const real = resource('/solicitantes', 'POST', 201);
  let clicked = false;
  const capture = apiResponse(events.page, '/solicitantes', async () => {
    clicked = true;
    await events.emit(real.route);
  }, 'POST');
  await Promise.resolve();
  assert.equal(clicked, false);
  registration.resolve();
  assert.equal((await capture).status(), 201);
  assert.equal(await (await capture).request().headerValue('Idempotency-Key'), 'idempotency-fixture');
});

test('filtra ruta, origen y método; conserva errores reales y no repite la mutación', async () => {
  const events = routing();
  const wrongPath = resource('/solicitantes', 'POST');
  const wrongMethod = resource('/consentimientos', 'GET');
  const foreign = resource('/consentimientos', 'POST');
  foreign.route.request().url = () => 'https://foreign.example/api/v1/consentimientos';
  const denied = resource('/consentimientos', 'POST', 403, '{"code":"FEATURE_NOT_IN_PLAN"}');
  const repeated = resource('/consentimientos', 'POST');
  const response = await apiResponse(events.page, '/consentimientos', async () => {
    await events.emit(wrongPath.route);
    await events.emit(wrongMethod.route);
    await events.emit(foreign.route);
    await events.emit(denied.route);
    await events.emit(repeated.route);
  }, 'POST');
  assert.equal(response.status(), 403);
  assert.deepEqual(await response.json(), { code: 'FEATURE_NOT_IN_PLAN' });
  assert.equal(wrongPath.state.fetches + wrongMethod.state.fetches + foreign.state.fetches, 0);
  assert.equal(wrongMethod.state.fallbacks, 1);
  assert.equal(repeated.state.fallbacks, 1);
  assert.equal(denied.state.fetches, 1);
});

test('un fallo real rechaza la captura, limpia la ruta y no expone credenciales', async () => {
  const events = routing();
  const real = resource('/tenant/actual');
  real.fail(new Error('Network error Authorization: Bearer fixture'));
  await assert.rejects(apiResponse(events.page, '/tenant/actual', () => events.emit(real.route)), error => {
    assert.ok(error instanceof Error);
    assert.match(error.message, /No se pudo capturar/);
    assert.doesNotMatch(error.message, /Bearer|fixture/);
    return true;
  });
  assert.equal(real.state.fulfilled, 0);
  assert.equal(real.state.aborted, 1);
  assert.equal(events.removals, 1);
});

test('una acción fallida o una respuesta ausente limpia la captura sin sustituir datos', async () => {
  const events = routing();
  await assert.rejects(apiResponse(events.page, '/tenant/actual', async () => { throw new Error('Click fallido'); }), /Click fallido/);
  assert.equal(events.removals, 1);
  await assert.rejects(apiResponse(events.page, '/tenant/actual', async () => {}, 'GET', 10), /No llegó una respuesta/);
  assert.equal(events.removals, 2);
});

test('una redirección se conserva como fallo HTTP; nunca se transforma en un 200', async () => {
  const events = routing();
  const real = resource('/tenant/actual', 'GET', 302, '');
  const response = await apiResponse(events.page, '/tenant/actual', () => events.emit(real.route));
  assert.equal(response.status(), 302);
  await assert.rejects(response.json());
});
