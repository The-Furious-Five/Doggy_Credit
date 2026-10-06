import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { Page, Response } from '@playwright/test';
import { apiResponse } from '../tests/api-response.js';

// Modela el recurso de Chromium: la primera lectura depende del documento;
// Playwright conserva el cuerpo leído para las siguientes llamadas a json().
function responseResource(path: string, method: string, status: number, payload: unknown) {
  let available = true;
  let content: Promise<Buffer> | undefined;
  const body = () => content ??= available
    ? Promise.resolve(Buffer.from(JSON.stringify(payload)))
    : Promise.reject(new Error('Network.getResponseBody: No data found for resource'));
  const response = {
    url: () => `https://localhost/api/v1${path}`,
    request: () => ({ method: () => method }),
    status: () => status,
    body,
    json: async () => JSON.parse((await body()).toString('utf8')),
  } as Response;
  return { response, navigate: () => { available = false; } };
}

// Entrega el evento antes de terminar el clic/navegación, como waitForResponse.
function responseEvents() {
  let receive: (response: Response) => Promise<void>;
  const page = {
    waitForResponse: (predicate: (response: Response) => boolean | Promise<boolean>) =>
      new Promise<Response>((resolve, reject) => {
        receive = async response => {
          try {
            if (await predicate(response)) resolve(response);
          } catch (error) {
            reject(error);
          }
        };
      }),
  } as Page;
  return { page, emit: (response: Response) => receive(response) };
}

for (const path of ['/tenant/actual', '/analytics/resumen', '/auditoria']) {
  test(`${path}: json sigue disponible si la navegación termina antes de consumir la respuesta`, async () => {
    const events = responseEvents();
    const resource = responseResource(path, 'GET', 200, { plan: 'FREE', items: [] });
    const captured = apiResponse(events.page, path);
    const arrival = events.emit(resource.response);
    resource.navigate();
    await arrival;
    const response = await captured;
    assert.equal(response.status(), 200);
    assert.deepEqual(await response.json(), { plan: 'FREE', items: [] });
  });
}

test('ignora otras rutas y métodos y conserva la respuesta de error real', async () => {
  const events = responseEvents();
  const captured = apiResponse(events.page, '/consentimientos', 'POST');
  for (const [path, method] of [['/consentimientos', 'GET'], ['/solicitantes', 'POST']]) {
    const unrelated = responseResource(path, method, 200, {});
    unrelated.navigate(); // Leer un cuerpo que no corresponde haría fallar la espera.
    await events.emit(unrelated.response);
  }
  const denied = responseResource('/consentimientos', 'POST', 403, { code: 'FEATURE_NOT_IN_PLAN' });
  const arrival = events.emit(denied.response);
  denied.navigate();
  await arrival;
  const response = await captured;
  assert.equal(response.status(), 403);
  assert.deepEqual(await response.json(), { code: 'FEATURE_NOT_IN_PLAN' });
});

test('un cuerpo ya perdido falla sin ocultar el error ni sustituir datos', async () => {
  const events = responseEvents();
  const lost = responseResource('/tenant/actual', 'GET', 200, {});
  lost.navigate();
  const captured = apiResponse(events.page, '/tenant/actual');
  const rejected = assert.rejects(captured, /Network.getResponseBody/);
  await events.emit(lost.response);
  await rejected;
});
