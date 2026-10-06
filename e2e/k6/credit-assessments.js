import { check, fail } from 'k6';
import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import execution from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { buildOptions, isolationResult, loadConfig, renderSummary, safeJson, validAssessment, validateFixture } from './support.mjs';

const fixture = validateFixture(JSON.parse(open('./.generated/seed.json')));
const config = loadConfig(__ENV);
export const options = buildOptions(config, __ENV.K6_INSECURE_TLS === 'true');

const assessmentDuration = new Trend('assessment_duration', true);
const assessmentRequests = new Counter('assessment_requests');
const assessmentSuccess = new Rate('assessment_success');
const isolationRequests = new Counter('isolation_requests');
const isolationSuccess = new Rate('isolation_success');
const tenantCrossings = new Counter('tenant_crossings');
const rateLimited = new Counter('rate_limited');
const tokens = {};

function ensure(condition, message) {
  if (!condition) fail(message);
}

function authenticate(tenant) {
  const cached = tokens[tenant.id];
  if (cached && cached.expiresAt > Date.now() + 30_000) return cached.token;
  const secret = __ENV[tenant.secretEnv];
  ensure(Boolean(secret), `Falta la variable de demo ${tenant.secretEnv}`);
  const response = http.post(`${config.origin}/auth/realms/doggycredit/protocol/openid-connect/token`, {
    grant_type: 'client_credentials', client_id: tenant.clientId, client_secret: secret,
  }, { tags: { name: 'oidc_token', phase: 'auth', plan: tenant.plan }, timeout: '10s', redirects: 0,
    responseCallback: http.expectedStatuses(200) });
  const body = safeJson(response);
  ensure(response.status === 200 && typeof body?.access_token === 'string', `M2M ${tenant.plan}: HTTP ${response.status}`);
  let claims;
  try { claims = JSON.parse(encoding.b64decode(body.access_token.split('.')[1], 'rawurl', 's')); } catch { fail('Token M2M inválido'); }
  ensure(claims.tenant_id === tenant.id, `El JWT de ${tenant.plan} tiene un tenant inesperado`);
  ensure(Number.isFinite(body.expires_in) && body.expires_in > 30, 'Vigencia del token insuficiente');
  tokens[tenant.id] = { token: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
  return body.access_token;
}

function request(method, path, tenant, name, expected, body = null, extraHeaders = {}) {
  const response = http.request(method, `${config.origin}/api/v1${path}`, body === null ? null : JSON.stringify(body), {
    headers: { Authorization: `Bearer ${authenticate(tenant)}`, 'Content-Type': 'application/json', ...extraHeaders },
    tags: { name, plan: tenant.plan }, timeout: '10s', redirects: 0,
    responseCallback: http.expectedStatuses(expected),
  });
  rateLimited.add(response.status === 429 ? 1 : 0, { plan: tenant.plan });
  return response;
}

function newAssessment(tenant, consentId) {
  return request('POST', '/credit-assessments', tenant, 'assessment_create', 201, {
    titularDocumento: fixture.document, consentId, proposito: 'Capital de trabajo', montoSolicitado: '5000', plazoMeses: 12,
  }, { 'Idempotency-Key': `k6-${crypto.sha256(crypto.randomBytes(16), 'hex')}` });
}

export function setup() {
  tenantCrossings.add(0);
  rateLimited.add(0);
  // Validar todas las referencias antes de mutar el backend.
  for (const tenant of fixture.tenants) ensure(Boolean(__ENV[tenant.secretEnv]), `Falta ${tenant.secretEnv}`);
  const prepared = fixture.tenants.map(tenant => {
    const current = request('GET', '/tenant/actual', tenant, 'tenant_current', 200);
    const actual = safeJson(current);
    ensure(current.status === 200 && actual?.id === tenant.id && actual.plan === tenant.plan, `Tenant ${tenant.plan} incorrecto`);
    const listed = request('GET', '/solicitantes?pagina=1&tamano=100', tenant, 'applicants_search', 200, null,
      { 'X-Documento': fixture.document });
    ensure(listed.status === 200 && Array.isArray(safeJson(listed)?.items), `Buscar solicitante ${tenant.plan}: HTTP ${listed.status}`);
    let applicant = safeJson(listed).items.find(item => item.documento === fixture.document);
    if (!applicant) {
      const created = request('POST', '/solicitantes', tenant, 'applicant_create', 201, {
        documento: fixture.document, actividad: 'Comercio (demo k6)', antiguedadActividadMeses: 60,
        ingresosMensuales: '9000', moneda: 'BOB', destinoCredito: 'Capital de trabajo', segmento: 'PERSONA',
      });
      ensure(created.status === 201, `Alta ${tenant.plan}: HTTP ${created.status}`);
      applicant = safeJson(created);
    }
    ensure(Boolean(applicant?.id), `Falta solicitante ${tenant.plan}`);
    const granted = request('POST', '/consentimientos', tenant, 'consent_create', 201, {
      titularDocumento: fixture.document, fuentes: ['BURO'], finalidad: 'Prueba sintética E-07', vigenciaDias: 1,
    });
    const consent = safeJson(granted);
    ensure(granted.status === 201 && consent?.estado === 'ACTIVO' && Boolean(consent.id), `Consentimiento ${tenant.plan}: HTTP ${granted.status}`);
    return { ...tenant, applicantId: applicant.id, consentId: consent.id };
  });
  ensure(prepared[0].applicantId !== prepared[1].applicantId, 'El mismo documento produjo un ID compartido entre tenants');
  for (let index = 0; index < prepared.length; index++) {
    const own = prepared[index];
    const other = prepared[1 - index];
    const allowed = request('GET', `/solicitantes/${own.applicantId}`, own, 'applicant_own', 200);
    ensure(allowed.status === 200 && safeJson(allowed)?.documento === fixture.document, `Perfil propio ${own.plan} inaccesible`);
    const foreign = request('GET', `/solicitantes/${own.applicantId}`, other, 'applicant_foreign', 404);
    const applicantIsolation = isolationResult(foreign.status, safeJson(foreign), 'UNKNOWN_APPLICANT');
    tenantCrossings.add(applicantIsolation.crossing ? 1 : 0);
    ensure(applicantIsolation.passed, `Aislamiento de solicitantes: HTTP ${foreign.status}, esperado 404 UNKNOWN_APPLICANT`);
    const denied = request('POST', '/credit-assessments', own, 'consent_foreign', 422, {
      titularDocumento: fixture.document, consentId: other.consentId, proposito: 'Capital de trabajo',
    }, { 'Idempotency-Key': `k6-${crypto.sha256(crypto.randomBytes(16), 'hex')}` });
    tenantCrossings.add(isolationResult(denied.status, safeJson(denied), 'CONSENT_REQUIRED').crossing ? 1 : 0);
    ensure(denied.status === 422 && safeJson(denied)?.code === 'CONSENT_REQUIRED', `Consentimiento ajeno ${own.plan}: HTTP ${denied.status}`);
  }
  // Solo IDs públicos. Cada VU obtiene sus tokens en memoria; nada se escribe en fixtures/reportes.
  return prepared;
}

export function evaluate(prepared) {
  const plan = execution.scenario.name.toUpperCase();
  const own = prepared.find(tenant => tenant.plan === plan);
  const other = prepared.find(tenant => tenant.plan !== plan);
  const tags = { plan };
  const created = newAssessment(own, own.consentId);
  const assessment = safeJson(created);
  const valid = created.status === 201 && validAssessment(assessment);
  assessmentRequests.add(1, tags);
  assessmentDuration.add(created.timings.duration, tags);
  assessmentSuccess.add(valid, tags);
  check(created, { 'POST 201 con score y decisión válidos': () => valid }, tags);
  if (!valid) return; // Los contadores mínimos de aislamiento detectan estas iteraciones incompletas.
  const ownRead = request('GET', `/credit-assessments/${assessment.id}`, own, 'assessment_own', 200);
  check(ownRead, { 'La evaluación propia conserva ID y score': response =>
    response.status === 200 && safeJson(response)?.id === assessment.id && safeJson(response)?.score === assessment.score }, tags);
  const foreign = request('GET', `/credit-assessments/${assessment.id}`, other, 'assessment_foreign', 404);
  const isolated = isolationResult(foreign.status, safeJson(foreign));
  isolationRequests.add(1, tags);
  isolationSuccess.add(isolated.passed, tags);
  tenantCrossings.add(isolated.crossing ? 1 : 0, tags);
  check(foreign, { 'El otro tenant recibe 404, sin datos de la evaluación': () => isolated.passed }, tags);
}

export function teardown(prepared) {
  if (!prepared) return;
  for (const tenant of prepared) {
    const revoked = request('POST', `/consentimientos/${tenant.consentId}/revocar`, tenant, 'consent_revoke', 200);
    check(revoked, { 'Consentimiento de carga revocado': response => response.status === 200 && safeJson(response)?.estado === 'REVOCADO' });
  }
}

export function handleSummary(data) {
  const report = renderSummary(data, config, { commit: __ENV.K6_COMMIT });
  return {
    stdout: report.markdown,
    'results/summary.json': JSON.stringify(data, null, 2),
    'results/result.md': report.markdown,
  };
}
