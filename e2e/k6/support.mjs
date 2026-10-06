export const PLANS = ['FREE', 'PRO'];

export function validateFixture(fixture) {
  if (typeof fixture.document !== 'string' || fixture.document.length < 3 || fixture.tenants?.length !== 2) {
    throw new Error('Genera el fixture k6 desde seed/tenants.yaml antes de ejecutar');
  }
  for (const plan of PLANS) {
    const matches = fixture.tenants.filter(tenant => tenant.plan === plan);
    if (matches.length !== 1 || ['id', 'key', 'clientId', 'secretEnv'].some(field => !matches[0][field])) {
      throw new Error(`Falta el tenant ${plan} o su cliente M2M en el fixture`);
    }
  }
  for (const field of ['id', 'key', 'clientId']) {
    if (fixture.tenants[0][field] === fixture.tenants[1][field]) throw new Error(`Fixture con ${field} duplicado`);
  }
  return fixture;
}

export function loadConfig(env) {
  const origin = (env.K6_BASE_URL || 'https://localhost').replace(/\/$/, '');
  // Solo un origen; evita credenciales o parámetros dentro de URLs de métricas/reportes.
  if (!/^https?:\/\/(?:localhost|[a-z0-9.-]+)(?::\d+)?$/i.test(origin)) {
    throw new Error('K6_BASE_URL debe ser un origen HTTP(S) sin credenciales, ruta ni query');
  }
  const rate = Number(env.K6_ITERATIONS_PER_MINUTE || 6);
  const minutes = Number(env.K6_MINUTES || 4);
  if (!Number.isInteger(rate) || rate < 1 || !Number.isInteger(minutes) || minutes < 1) {
    throw new Error('K6_ITERATIONS_PER_MINUTE y K6_MINUTES deben ser enteros positivos');
  }
  return { origin, rate, minutes, samples: rate * minutes, p95Ms: 2000 };
}

export function buildOptions(config, insecureTLS = false) {
  const thresholds = {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    dropped_iterations: ['count==0'],
    tenant_crossings: ['count==0'],
    rate_limited: ['count==0'],
    assessment_duration: ['p(95)<2000'],
  };
  const scenarios = {};
  for (const plan of PLANS) {
    scenarios[plan.toLowerCase()] = {
      executor: 'constant-arrival-rate', exec: 'evaluate', rate: config.rate,
      timeUnit: '1m', duration: `${config.minutes}m`, startTime: '0s',
      preAllocatedVUs: 1, maxVUs: 2, gracefulStop: '30s', tags: { plan },
    };
    thresholds[`assessment_duration{plan:${plan}}`] = ['p(95)<2000'];
    thresholds[`assessment_requests{plan:${plan}}`] = [`count>=${config.samples}`];
    thresholds[`assessment_success{plan:${plan}}`] = ['rate==1'];
    thresholds[`isolation_requests{plan:${plan}}`] = [`count>=${config.samples}`];
    thresholds[`isolation_success{plan:${plan}}`] = ['rate==1'];
  }
  return {
    scenarios, thresholds, setupTimeout: '90s', teardownTimeout: '30s',
    insecureSkipTLSVerify: insecureTLS, summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)'],
    // Los IDs de recursos y el documento no se convierten en etiquetas de alta cardinalidad.
    systemTags: ['name', 'method', 'status', 'scenario', 'expected_response'],
  };
}

export function safeJson(response) {
  try { return JSON.parse(response.body); } catch { return null; }
}

export function validAssessment(body) {
  return typeof body?.id === 'string' && body.id.length > 0 &&
    Number.isFinite(body.score) && body.score >= 0 && body.score <= 1000 &&
    ['A', 'B', 'C', 'D', 'E'].includes(body.banda) &&
    ['PREAPROBADO', 'REVISION_MANUAL'].includes(body.estado) &&
    ['PREAPROBADO', 'REVISION_MANUAL', 'SOLICITAR_DATOS'].includes(body.decision);
}

export function isolationResult(status, body, expectedCode = 'ASSESSMENT_NOT_FOUND') {
  // Exigir el ProblemDetail del catálogo evita aceptar un 404 del proxy o con datos ajenos.
  const fields = ['type', 'title', 'status', 'detail', 'instance', 'code', 'correlationId', 'timestamp'];
  const unexpectedData = body && typeof body === 'object' && Object.keys(body).some(key => !fields.includes(key));
  const crossing = (status >= 200 && status < 300) || Boolean(unexpectedData);
  return { passed: status === 404 && body?.status === 404 && body.code === expectedCode && !crossing, crossing };
}

export function renderSummary(data, config, metadata = {}) {
  const metrics = data.metrics || {};
  const value = (name, stat) => metrics[name]?.values?.[stat];
  const failures = Object.entries(metrics).flatMap(([name, metric]) =>
    Object.entries(metric.thresholds || {}).filter(([, threshold]) => threshold.ok !== true)
      .map(([threshold]) => `${name}: ${threshold}`));
  const rows = PLANS.map(plan => ({
    plan, attempts: value(`assessment_requests{plan:${plan}}`, 'count'),
    p95: value(`assessment_duration{plan:${plan}}`, 'p(95)'),
    success: value(`assessment_success{plan:${plan}}`, 'rate'),
    isolation: value(`isolation_requests{plan:${plan}}`, 'count'),
    isolated: value(`isolation_success{plan:${plan}}`, 'rate'),
  }));
  const complete = rows.every(row => Number.isFinite(row.p95) && row.attempts >= config.samples && row.isolation >= config.samples &&
    Number.isFinite(row.success) && Number.isFinite(row.isolated));
  const observed = rows.every(row => row.p95 < config.p95Ms && row.success === 1 && row.isolated === 1) &&
    value('tenant_crossings', 'count') === 0 && value('rate_limited', 'count') === 0 &&
    (value('dropped_iterations', 'count') ?? 0) === 0;
  // Un resumen sin thresholds evaluados (setup abortado o fixture incompleto) no pasa.
  const thresholdsPresent = Object.entries(buildOptions(config).thresholds).every(([name, expected]) =>
    expected.every(threshold => typeof metrics[name]?.thresholds?.[threshold]?.ok === 'boolean'));
  const status = !complete || !thresholdsPresent ? 'INCOMPLETO' : failures.length || !observed ? 'FALLÓ' : 'PASÓ';
  const display = (number, suffix = '') => Number.isFinite(number) ? `${number.toFixed(2)}${suffix}` : 'sin muestras';
  const markdown = [
    '# E-07 · Resultado de carga k6', '', `Estado: **${status}**`, '',
    `Fecha UTC: ${metadata.date || new Date().toISOString()}`,
    `Commit: ${metadata.commit || 'no indicado'}`, `Origen: ${config.origin}`, '',
    `Dos escenarios paralelos: ${config.rate} iteraciones/min por tenant durante ${config.minutes} min.`,
    'Cada iteración crea una evaluación nueva, la lee con su token y la solicita con el token del otro tenant.',
    'El p95 incluye todas las respuestas del POST medido; setup y lecturas se miden aparte.', '',
    '| Plan | POST medidos | p95 POST (ms) | Éxito | Lecturas cruzadas | Aislamiento |',
    '| --- | ---: | ---: | ---: | ---: | ---: |',
    ...rows.map(row => `| ${row.plan} | ${display(row.attempts)} | ${display(row.p95)} | ${display(row.success * 100, ' %')} | ${display(row.isolation)} | ${display(row.isolated * 100, ' %')} |`), '',
    `Cruces de datos: ${display(value('tenant_crossings', 'count'))}`,
    `Respuestas 429: ${display(value('rate_limited', 'count'))}`,
    `Iteraciones no iniciadas: ${display(value('dropped_iterations', 'count') ?? 0)}`, '',
    'Criterio: p95 < 2000 ms en ambos tenants, éxito 100 %, cero cruces, cero 429 y todas las iteraciones ejecutadas.', '',
    ...(failures.length ? ['Umbrales incumplidos:', '', ...failures.map(failure => `- ${failure}`), ''] : []),
    'El JSON adjunto conserva los valores y thresholds de k6. No contiene tokens ni secretos.', '',
  ].join('\n');
  return { status, markdown };
}
