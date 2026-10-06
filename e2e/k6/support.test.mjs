import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildOptions, isolationResult, loadConfig, renderSummary, safeJson, validAssessment, validateFixture } from './support.mjs';

const config = loadConfig({});
const fixture = { document: 'DOC-SYNTHETIC', tenants: [
  { id: 'tenant-a', key: 'a', plan: 'FREE', clientId: 'client-a', secretEnv: 'SECRET_A' },
  { id: 'tenant-b', key: 'b', plan: 'PRO', clientId: 'client-b', secretEnv: 'SECRET_B' },
] };

// Datos unitarios, no mediciones de la API: solo comprueban la clasificación del reporte.
function completeSummary() {
  const metrics = {};
  for (const [name, thresholds] of Object.entries(buildOptions(config).thresholds)) {
    metrics[name] = { values: {}, thresholds: Object.fromEntries(thresholds.map(threshold => [threshold, { ok: true }])) };
  }
  for (const plan of ['FREE', 'PRO']) {
    metrics[`assessment_duration{plan:${plan}}`].values = { 'p(95)': 100 };
    metrics[`assessment_requests{plan:${plan}}`].values = { count: config.samples };
    metrics[`assessment_success{plan:${plan}}`].values = { rate: 1 };
    metrics[`isolation_requests{plan:${plan}}`].values = { count: config.samples };
    metrics[`isolation_success{plan:${plan}}`].values = { rate: 1 };
  }
  for (const name of ['tenant_crossings', 'rate_limited', 'dropped_iterations']) metrics[name].values = { count: 0 };
  return { metrics };
}

test('cada tenant tiene que ser distinto y pertenecer al plan esperado', () => {
  assert.deepEqual(validateFixture(fixture), fixture);
  for (const field of ['id', 'key', 'clientId']) {
    const bad = structuredClone(fixture);
    bad.tenants[1][field] = bad.tenants[0][field];
    assert.throws(() => validateFixture(bad), /duplicado/);
  }
  assert.throws(() => validateFixture({ ...fixture, tenants: fixture.tenants.slice(0, 1) }));
});

test('rechaza configuración inválida y URLs que podrían exponer credenciales', () => {
  assert.equal(config.samples, 24);
  for (const origin of ['https://user:password@localhost', 'https://localhost/path', 'https://localhost?token=secret']) {
    assert.throws(() => loadConfig({ K6_BASE_URL: origin }), /sin credenciales/);
  }
  for (const value of ['0', '-1', '1.5', 'invalid']) {
    assert.throws(() => loadConfig({ K6_MINUTES: value }));
    assert.throws(() => loadConfig({ K6_ITERATIONS_PER_MINUTE: value }));
  }
});

test('solo un 404 autenticado demuestra aislamiento; un 2xx demuestra un cruce', () => {
  const problem = { status: 404, code: 'ASSESSMENT_NOT_FOUND', detail: 'No existe o pertenece a otro tenant' };
  assert.deepEqual(isolationResult(404, problem), { passed: true, crossing: false });
  for (const status of [0, 401, 403, 429, 500]) assert.deepEqual(isolationResult(status), { passed: false, crossing: false });
  for (const status of [200, 201, 204]) assert.deepEqual(isolationResult(status), { passed: false, crossing: true });
  assert.equal(isolationResult(404, null).passed, false);
  assert.equal(isolationResult(404, { ...problem, code: 'OTHER_ERROR' }).passed, false);
  assert.deepEqual(isolationResult(404, { ...problem, score: 700 }), { passed: false, crossing: true });
  assert.deepEqual(isolationResult(404, { ...problem, data: { id: 'foreign' } }), { passed: false, crossing: true });
});

test('un 201 sin score o decisión válidos no cuenta como evaluación satisfactoria', () => {
  const assessment = { id: 'assessment-demo', score: 700, banda: 'B', estado: 'PREAPROBADO', decision: 'PREAPROBADO' };
  assert.equal(validAssessment(assessment), true);
  for (const patch of [{ score: '700' }, { score: 1001 }, { score: -1 }, { id: '' }, { banda: 'INVALID' },
    { decision: 'RECHAZADO' }, { estado: 'PARCIAL' }, { estado: 'FALLIDA' }]) {
    assert.equal(validAssessment({ ...assessment, ...patch }), false);
  }
  assert.equal(safeJson({ body: '<html>error</html>' }), null);
});

test('el runbook no aprueba una ejecución sin muestras o sin thresholds', () => {
  assert.equal(renderSummary({}, config).status, 'INCOMPLETO');
  const data = completeSummary();
  delete data.metrics['assessment_success{plan:FREE}'].thresholds;
  assert.equal(renderSummary(data, config).status, 'INCOMPLETO');
  const globalMissing = completeSummary();
  delete globalMissing.metrics.http_req_failed.thresholds;
  assert.equal(renderSummary(globalMissing, config).status, 'INCOMPLETO');
});

test('un tenant lento no queda oculto por el promedio del otro', () => {
  const data = completeSummary();
  data.metrics['assessment_duration{plan:FREE}'].values['p(95)'] = 2000;
  assert.equal(renderSummary(data, config).status, 'FALLÓ');
});

test('rechaza cruces, 429, errores, iteraciones perdidas o thresholds incumplidos', () => {
  for (const name of ['tenant_crossings', 'rate_limited', 'dropped_iterations']) {
    const data = completeSummary();
    data.metrics[name].values.count = 1;
    assert.equal(renderSummary(data, config).status, 'FALLÓ');
  }
  const data = completeSummary();
  data.metrics['assessment_success{plan:PRO}'].values.rate = 0.99;
  assert.equal(renderSummary(data, config).status, 'FALLÓ');
  const failed = completeSummary();
  failed.metrics.http_req_failed.thresholds['rate==0'].ok = false;
  assert.equal(renderSummary(failed, config).status, 'FALLÓ');
});

test('exige todas las comprobaciones de aislamiento antes de dar un resultado', () => {
  const data = completeSummary();
  data.metrics['isolation_requests{plan:FREE}'].values.count = config.samples - 1;
  assert.equal(renderSummary(data, config).status, 'INCOMPLETO');
  const report = renderSummary(completeSummary(), config, { commit: 'unit-test', date: 'unit-test' });
  assert.equal(report.status, 'PASÓ');
  assert.match(report.markdown, /p95 < 2000 ms/);
  assert.match(report.markdown, /FREE/);
  assert.match(report.markdown, /PRO/);
});
