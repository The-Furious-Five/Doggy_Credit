import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { BehaviorSubject, Observable, of } from 'rxjs';
import {
  AdminService,
  AnalyticsService,
  AuditoriaService,
  BASE_PATH,
  ClavesApiService,
  ConsentimientosService,
  EvaluacionesService,
  IntegracionesService,
  SolicitantesService,
  TenantService,
} from '../api';
import { appConfig } from '../app.config';
import { DsToastOutlet, DsToastService } from '../design-system';
import { HANDLED_PROBLEMS } from './api-error.interceptor';
import { TenantSuspensionService } from './tenant-suspension.service';

describe('Interceptores HTTP registrados en la aplicación', () => {
  let http: HttpClient;
  let requests: HttpTestingController;
  let toast: DsToastService;
  let api: string;
  const oidc = { getAccessToken: vi.fn() };

  beforeEach(() => {
    oidc.getAccessToken.mockReset().mockReturnValue(of('jwt-vigente'));
    TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers,
        provideHttpClientTesting(),
        { provide: OidcSecurityService, useValue: oidc },
      ],
    });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(DsToastService);
    api = TestBed.inject(BASE_PATH);
  });

  afterEach(() => requests.verify());

  it('añade el Bearer a las peticiones del cliente OpenAPI existente', () => {
    TestBed.inject(TenantService).obtenerTenantActual().subscribe();
    const request = requests.expectOne(`${api}/tenant/actual`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-vigente');
    expect(request.request.headers.get('Accept')).toContain('application/json');
    request.flush({ nombre: 'Institución de prueba' });
    expect(toast.message()).toBe('');
  });

  it.each([
    [
      'analytics',
      () => TestBed.inject(AnalyticsService).obtenerResumenAnalytics(),
      '/analytics/resumen',
    ],
    [
      'solicitantes',
      () => TestBed.inject(SolicitantesService).listarSolicitantes(),
      '/solicitantes',
    ],
    [
      'consentimientos',
      () => TestBed.inject(ConsentimientosService).listarConsentimientos('cliente-prueba'),
      '/consentimientos',
    ],
    [
      'evaluaciones',
      () => TestBed.inject(EvaluacionesService).obtenerEvaluacion('evaluacion-prueba'),
      '/credit-assessments/evaluacion-prueba',
    ],
    [
      'integraciones',
      () => TestBed.inject(IntegracionesService).listarIntegraciones(),
      '/integraciones',
    ],
    ['claves de API', () => TestBed.inject(ClavesApiService).listarClavesApi(), '/api-keys'],
    ['administración', () => TestBed.inject(AdminService).listarTenants(), '/admin/tenants'],
    ['auditoría', () => TestBed.inject(AuditoriaService).listarAuditoria(), '/auditoria'],
  ] as const)(
    'conecta %s con la API y aplica JWT y Problem Details globalmente',
    (_name, send: () => Observable<unknown>, path) => {
      let received: HttpErrorResponse | undefined;
      send().subscribe({ error: (error: HttpErrorResponse) => (received = error) });
      const request = requests.expectOne((candidate) => candidate.url === `${api}${path}`);
      expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-vigente');
      request.flush(
        { title: 'Rol insuficiente', correlationId: 'id-compartido' },
        {
          status: 403,
          statusText: 'Forbidden',
          headers: { 'Content-Type': 'application/problem+json' },
        },
      );
      expect(received!.status).toBe(403);
      expect(toast.message()).toContain('Rol insuficiente');
      expect(toast.message()).toContain('correlationId: id-compartido');
    },
  );

  it.each(['/api/v1', '/api/v1/clientes?buscar=prueba'])(
    'protege también la URL relativa %s',
    (url) => {
      http.get(url).subscribe();
      const request = requests.expectOne(url);
      expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-vigente');
      request.flush({});
    },
  );

  it('lee el token renovado en cada petición', () => {
    http.get(`${api}/primera`).subscribe();
    const first = requests.expectOne(`${api}/primera`);
    expect(first.request.headers.get('Authorization')).toBe('Bearer jwt-vigente');
    first.flush({});

    oidc.getAccessToken.mockReturnValue(of('jwt-renovado'));
    http.get(`${api}/segunda`).subscribe();
    const second = requests.expectOne(`${api}/segunda`);
    expect(second.request.headers.get('Authorization')).toBe('Bearer jwt-renovado');
    second.flush({});
  });

  it('no duplica una petición pendiente si el observable del token vuelve a emitir', () => {
    const token = new BehaviorSubject('jwt-inicial');
    oidc.getAccessToken.mockReturnValue(token);
    http.get(`${api}/clientes`).subscribe();
    const request = requests.expectOne(`${api}/clientes`);
    token.next('jwt-renovado');
    requests.expectNone(`${api}/clientes`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-inicial');
    request.flush({});
  });

  it('no inventa credenciales cuando no hay token', () => {
    oidc.getAccessToken.mockReturnValue(of(''));
    http.get(`${api}/clientes`).subscribe();
    const request = requests.expectOne(`${api}/clientes`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it.each([
    '/api/v10/clientes',
    '/api/v1-externa/clientes',
    '/assets/logo.svg',
    '/auth/realms/doggycredit/protocol/openid-connect/token',
    'https://externo.invalid/api/v1/clientes',
    '//externo.invalid/api/v1/clientes',
    `${window.location.origin}.externo.invalid/api/v1/clientes`,
  ])('no envía JWT ni muestra toasts de la API para %s', (url) => {
    http.get(url).subscribe({ error: () => undefined });
    const request = requests.expectOne(url);
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(oidc.getAccessToken).not.toHaveBeenCalled();
    request.flush(
      { title: 'Error externo', correlationId: 'id-externo' },
      {
        status: 500,
        statusText: 'Server Error',
      },
    );
    expect(toast.message()).toBe('');
  });

  it('muestra el RFC 9457 con correlationId y conserva el error para la vista', () => {
    const outlet = TestBed.createComponent(DsToastOutlet);
    const problem = {
      type: 'https://docs.doggycredit.example/errors/consent-required',
      title: 'Consentimiento requerido',
      status: 422,
      detail: 'No hay consentimiento vigente para la fuente banco.',
      code: 'CONSENT_REQUIRED',
      correlationId: 'correlacion-prueba',
    };
    let received: HttpErrorResponse | undefined;
    http.post(`${api}/evaluaciones`, {}).subscribe({ error: (error) => (received = error) });
    requests.expectOne(`${api}/evaluaciones`).flush(problem, {
      status: 422,
      statusText: 'Unprocessable Entity',
      headers: { 'Content-Type': 'application/problem+json', 'X-Correlation-Id': 'otro-id' },
    });
    outlet.detectChanges();
    expect(received).toBeInstanceOf(HttpErrorResponse);
    expect(received!.error).toEqual(problem);
    expect(received!.status).toBe(422);
    const message = outlet.nativeElement.querySelector('.ds-toast').textContent;
    expect(message).toContain(problem.title);
    expect(message).toContain(problem.detail);
    expect(message).toContain('correlationId: correlacion-prueba');
    expect(message).not.toContain('otro-id');
  });

  it('no repite en un toast los códigos que la vista presenta, pero sí los demás errores', () => {
    const context = new HttpContext().set(HANDLED_PROBLEMS, ['FEATURE_NOT_IN_PLAN']);
    let received: HttpErrorResponse | undefined;
    http.get(`${api}/webhooks`, { context }).subscribe({ error: (error) => (received = error) });
    requests.expectOne(`${api}/webhooks`).flush(
      { title: 'Función no disponible en el plan actual', code: 'FEATURE_NOT_IN_PLAN' },
      { status: 403, statusText: 'Forbidden' },
    );
    expect(received?.status).toBe(403);
    expect(toast.message()).toBe('');

    http.get(`${api}/webhooks`, { context }).subscribe({ error: () => undefined });
    requests.expectOne(`${api}/webhooks`).flush(
      { title: 'Error interno', code: 'INTERNAL_ERROR' },
      { status: 500, statusText: 'Server Error' },
    );
    expect(toast.message()).toContain('Error interno');
  });

  it('TENANT_SUSPENDED activa el aviso de suspensión y no muestra el toast genérico', () => {
    http.get(`${api}/clientes`).subscribe({ error: () => undefined });
    requests.expectOne(`${api}/clientes`).flush(
      { title: 'Tenant suspendido', code: 'TENANT_SUSPENDED' },
      { status: 403, statusText: 'Forbidden' },
    );
    expect(TestBed.inject(TenantSuspensionService).suspended()).toBe(true);
    expect(toast.message()).toBe('');
  });

  it('interpreta Problem Details de peticiones con responseType text', () => {
    http.get(`${api}/reporte`, { responseType: 'text' }).subscribe({ error: () => undefined });
    requests.expectOne(`${api}/reporte`).flush(
      JSON.stringify({
        title: 'Reporte no disponible',
        detail: 'Intenta nuevamente.',
        correlationId: 'id-texto',
      }),
      { status: 500, statusText: 'Server Error' },
    );
    expect(toast.message()).toContain('Reporte no disponible');
    expect(toast.message()).toContain('correlationId: id-texto');
  });

  it('usa el correlationId del encabezado si falta la extensión en el cuerpo', () => {
    http.get(`${api}/clientes`).subscribe({ error: () => undefined });
    requests.expectOne(`${api}/clientes`).flush(
      { title: 'Operación fallida' },
      {
        status: 500,
        statusText: 'Server Error',
        headers: { 'X-Correlation-Id': 'id-cabecera' },
      },
    );
    expect(toast.message()).toContain('correlationId: id-cabecera');
  });

  it.each([null, '<html>Error de proxy</html>', { title: {}, detail: [], correlationId: 7 }])(
    'maneja errores incompletos sin mostrar HTML ni valores inválidos (%s)',
    (body) => {
      http.get(`${api}/clientes`, { responseType: 'text' }).subscribe({ error: () => undefined });
      const response = typeof body === 'object' && body !== null ? JSON.stringify(body) : body;
      requests
        .expectOne(`${api}/clientes`)
        .flush(response, { status: 500, statusText: 'Server Error' });
      expect(toast.message()).toBe('No se pudo completar la solicitud. Inténtalo nuevamente.');
    },
  );

  it.each([
    [401, 'Tu sesión no es válida. Inicia sesión nuevamente.'],
    [403, 'No tienes permiso para realizar esta operación.'],
  ])('da una indicación útil para HTTP %s sin Problem Details', (status, message) => {
    http.get(`${api}/clientes`).subscribe({ error: () => undefined });
    requests
      .expectOne(`${api}/clientes`)
      .flush({}, { status: Number(status), statusText: 'Error' });
    expect(toast.message()).toBe(message);
  });

  it('explica los fallos de conexión sin ocultarlos a la vista', () => {
    let received: HttpErrorResponse | undefined;
    http.get(`${api}/clientes`).subscribe({ error: (error) => (received = error) });
    requests.expectOne(`${api}/clientes`).error(new ProgressEvent('error'));
    expect(received!.status).toBe(0);
    expect(toast.message()).toContain('Comprueba tu conexión');
  });
});
