import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of } from 'rxjs';
import {
  AnalyticsModeloEstadoEnum,
  AnalyticsNecesidadCategoriaEnum,
  AnalyticsProductoTipoEnum,
  AnalyticsResumen,
  BASE_PATH,
  EvaluacionResumenBandaEnum,
  EvaluacionResumenDecisionEnum,
  EvaluacionResumenEstadoEnum,
  TenantActualPlanEnum,
  TenantActualTipoEnum,
} from '../api';
import { routes } from '../app.routes';
import { AuthSession, AuthSessionService } from '../auth/session.service';
import { DsTheme, DsThemeService, DsToastService } from '../design-system';
import { apiErrorInterceptor } from '../http/api-error.interceptor';
import { jwtInterceptor } from '../http/jwt.interceptor';
import { AnalyticsDashboard } from './analytics-dashboard';
import { SensitiveDataService } from './sensitive-data.service';

const summary = (plan = TenantActualPlanEnum.Free): AnalyticsResumen => ({
  desde: '2026-09-01',
  hasta: '2026-09-30',
  plan,
  moneda: 'BOB',
  analiticaAvanzada: plan !== TenantActualPlanEnum.Free,
  evaluaciones: 34,
  scorePromedio: 644,
  integracionesActivas: 3,
  porcentajeConRecomendacion: 50,
  necesidades: [
    {
      categoria: AnalyticsNecesidadCategoriaEnum.InversionProductiva,
      cantidad: 9,
      porcentaje: 26.47,
    },
  ],
  ...(plan !== TenantActualPlanEnum.Free
    ? {
        pro: {
          conversionPorcentaje: 25,
          tiempoDecisionMedianaMs: 4800,
          montoRecomendado: '120500.00',
          recomendacionesPorProducto: [
            {
              tipo: AnalyticsProductoTipoEnum.Verde,
              cantidad: 17,
              porcentaje: 100,
              montoRecomendado: '120500.00',
            },
          ],
          modelos: [
            {
              version: 'scoring-contrato-e05',
              psi: 0.08,
              estado: AnalyticsModeloEstadoEnum.Estable,
              desde: '2026-09-21T04:00:00Z',
              hasta: '2026-09-28T04:00:00Z',
              muestrasActuales: 72,
              muestrasBase: 140,
            },
          ],
        },
      }
    : {}),
});

describe('E-05: Resumen y Analítica con shell y clientes HTTP generados', () => {
  let requests: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;
  const toast = { show: vi.fn() };
  const session = signal<AuthSession>({
    isAuthenticated: true,
    name: 'Analista E-05',
    tenantId: 'tenant-del-token',
    roles: ['ANALYST'],
  });

  beforeEach(() => {
    toast.show.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([apiErrorInterceptor, jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: BASE_PATH, useValue: '/api/v1' },
        { provide: AuthSessionService, useValue: { session, check: () => of(session()) } },
        { provide: OidcSecurityService, useValue: { getAccessToken: () => of('token-e05') } },
        { provide: DsToastService, useValue: toast },
        { provide: SensitiveDataService, useValue: { amount: (value: string) => value } },
      ],
    });
    requests = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    requests.verify();
    document.documentElement.removeAttribute('data-theme');
    localStorage.removeItem('dc-theme');
  });

  async function update(): Promise<void> {
    harness.detectChanges();
    TestBed.tick();
    // rxResource resuelve el stream de forma asíncrona. No esperar whenStable:
    // los tests de carga conservan otras peticiones abiertas deliberadamente.
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    harness.detectChanges();
  }

  async function navigate(path: string): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(path);
    page = harness.routeNativeElement!;
    await update();
  }

  function request(path: string): TestRequest {
    const result = requests.expectOne((r) => r.url === '/api/v1' + path);
    expect(result.request.method).toBe('GET');
    expect(result.request.headers.get('Authorization')).toBe('Bearer token-e05');
    expect(result.request.headers.has('X-Tenant-Id')).toBe(false);
    expect(result.request.params.has('tenantId')).toBe(false);
    return result;
  }

  async function loadShell(plan = TenantActualPlanEnum.Free): Promise<void> {
    request('/tenant/actual').flush({
      id: session().tenantId,
      nombre: 'Organización del contrato',
      nit: 'nit-ficticio',
      tipo: TenantActualTipoEnum.Imf,
      plan,
      pais: 'BO',
      moneda: 'BOB',
      activo: true,
    });
    request('/tenant/actual/uso').flush({ evaluacionesMes: 34, limiteMensual: 50 });
    await update();
  }

  async function loadIndicators(data = summary()): Promise<TestRequest> {
    const result = request('/analytics/resumen');
    expect(result.request.params.keys().sort()).toEqual(['desde', 'hasta']);
    expect(result.request.params.get('desde')).toMatch(/^\d{4}-\d{2}-01$/);
    expect(result.request.params.get('hasta')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    result.flush(data);
    await update();
    return result;
  }

  async function loadRecent(empty = false): Promise<void> {
    const result = request('/credit-assessments');
    expect(result.request.params.keys().sort()).toEqual(['pagina', 'tamano']);
    expect(result.request.params.get('pagina')).toBe('1');
    expect(result.request.params.get('tamano')).toBe('5');
    result.flush({
      total: empty ? 0 : 1,
      items: empty
        ? []
        : [
            {
              id: 'evaluacion-http',
              solicitanteId: 'cliente-http',
              creadoEn: '2026-09-30T16:00:00Z',
              score: 811,
              banda: EvaluacionResumenBandaEnum.A,
              estado: EvaluacionResumenEstadoEnum.Preaprobado,
              decision: EvaluacionResumenDecisionEnum.RevisionManual,
            },
          ],
    });
    await update();
  }

  function dashboard(): HTMLElement {
    return page.querySelector('dc-analytics-dashboard')!;
  }

  async function click(container: HTMLElement, label: string): Promise<void> {
    const button = [...container.querySelectorAll('button')].find((b) =>
      b.textContent?.includes(label),
    )!;
    button.click();
    await update();
  }

  const cases = (['/app/resumen', '/app/analitica'] as const).flatMap((path) =>
    ([TenantActualPlanEnum.Free, TenantActualPlanEnum.Pro] as const).flatMap((plan) =>
      (['light', 'dark'] as DsTheme[]).map((theme) => ({ path, plan, theme })),
    ),
  );

  it.each(cases)('$path usa los datos de $plan en tema $theme', async ({ path, plan, theme }) => {
    await navigate(path);
    TestBed.inject(DsThemeService).preference.set(theme);
    await loadShell(plan);
    await loadIndicators(summary(plan));
    if (path === '/app/resumen') await loadRecent();
    expect(document.documentElement.getAttribute('data-theme')).toBe(theme);
    expect(page.querySelector('.console-top')?.textContent).toContain('Organización del contrato');
    expect(dashboard().textContent).toContain('644');
    expect(dashboard().textContent).toContain('Inversión productiva · 9');
    expect(dashboard().textContent).not.toContain('Mora a 90 días');
    const progress = dashboard().querySelector('[role="progressbar"]')!;
    expect(progress.getAttribute('aria-valuenow')).toBe('26');
    expect(
      dashboard().querySelector('#' + progress.getAttribute('aria-labelledby'))?.textContent,
    ).toContain('Inversión productiva · 9');
    if (plan === TenantActualPlanEnum.Free) {
      expect(dashboard().textContent).toContain('Plan Gratis');
      expect(dashboard().textContent).toContain('Analítica avanzada disponible con Pro');
      expect(dashboard().querySelectorAll('ds-stat')).toHaveLength(4);
      expect(dashboard().querySelector('table')).toBeNull();
    } else {
      expect(dashboard().textContent).toContain('Plan Pro');
      expect(dashboard().querySelectorAll('ds-stat')).toHaveLength(7);
      expect(dashboard().textContent).toContain('Conversión');
      expect(dashboard().textContent).toContain('Crédito ecológico · 17');
      expect(dashboard().textContent).toContain('scoring-contrato-e05');
      expect(dashboard().textContent).not.toContain('Analítica avanzada disponible con Pro');
    }
    if (path === '/app/resumen') {
      const recent = page.querySelector('dc-assessment-list')!;
      expect(recent.textContent).toContain('811');
      expect(recent.textContent).toContain('Revisión manual');
      expect(recent.querySelector('a')?.getAttribute('href')).toBe('/app/clientes/cliente-http');
    }
  });

  it('habilita todas las secciones para Enterprise según la respuesta del servidor', async () => {
    await navigate('/app/analitica');
    await loadShell(TenantActualPlanEnum.Enterprise);
    await loadIndicators(summary(TenantActualPlanEnum.Enterprise));
    expect(dashboard().textContent).toContain('Plan Enterprise');
    expect(dashboard().textContent).toContain('Tiempo de decisión');
    expect(dashboard().textContent).toContain('4,8 s');
    expect(dashboard().textContent).toContain('Bs 120.500');
    expect(dashboard().textContent).toContain('Modelos');
  });

  it('carga KPIs y evaluaciones de manera independiente y conserva los KPIs ante un fallo de C-09', async () => {
    await navigate('/app/resumen');
    await loadShell();
    expect(dashboard().querySelector('ds-spinner')?.getAttribute('role')).toBe('status');
    expect(page.querySelector('dc-assessment-list ds-spinner')?.textContent).toContain(
      'Cargando evaluaciones',
    );
    expect(dashboard().querySelector('ds-stat')).toBeNull();
    await loadIndicators();
    request('/credit-assessments').flush(
      { title: 'No disponible', correlationId: 'trace-e05' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    await update();
    expect(dashboard().textContent).toContain('644');
    expect(page.querySelector('dc-assessment-list')?.textContent).toContain(
      'No pudimos cargar las evaluaciones',
    );
    expect(toast.show).toHaveBeenCalledWith(expect.stringContaining('correlationId: trace-e05'));
    await click(page.querySelector('dc-assessment-list')!, 'Reintentar');
    await loadRecent();
    expect(page.querySelector('dc-assessment-list')?.textContent).toContain('811');
  });

  it('conserva las evaluaciones si falla E-02 y reintenta el mismo intervalo', async () => {
    await navigate('/app/resumen');
    await loadShell();
    await loadRecent();
    const failed = request('/analytics/resumen');
    failed.flush({ title: 'No disponible' }, { status: 503, statusText: 'Service Unavailable' });
    await update();
    expect(dashboard().textContent).toContain('No pudimos cargar los indicadores');
    expect(dashboard().textContent).not.toContain('Analítica avanzada disponible con Pro');
    expect(page.querySelector('dc-assessment-list')?.textContent).toContain('811');
    await click(dashboard(), 'Reintentar');
    const retried = await loadIndicators();
    expect(retried.request.urlWithParams).toBe(failed.request.urlWithParams);
    expect(dashboard().textContent).toContain('644');
  });

  it('presenta las ausencias del servidor sin cifras simuladas ni avances ficticios', async () => {
    await navigate('/app/resumen');
    await loadShell(TenantActualPlanEnum.Pro);
    const empty = summary(TenantActualPlanEnum.Pro);
    empty.evaluaciones = 0;
    empty.scorePromedio = null;
    empty.necesidades = empty.necesidades.map((need) => ({ ...need, cantidad: 0, porcentaje: 0 }));
    empty.pro = {
      conversionPorcentaje: 0,
      montoRecomendado: '0',
      tiempoDecisionMedianaMs: null,
      recomendacionesPorProducto: [],
      modelos: [],
    };
    await loadIndicators(empty);
    await loadRecent(true);
    expect(dashboard().textContent).toContain('Sin evaluaciones en este período');
    expect(dashboard().textContent).toContain('Sin necesidades detectadas');
    expect(dashboard().textContent).toContain('Sin recomendaciones');
    expect(dashboard().textContent).toContain('Sin monitoreo de modelos');
    expect(page.querySelector('dc-assessment-list')?.textContent).toContain('Sin evaluaciones');
    expect(dashboard().querySelector('ds-progress')).toBeNull();
    expect(dashboard().querySelectorAll('ds-stat')[1].textContent).toContain('—');
  });

  it('cancela la consulta anterior al cambiar las fechas y actualizar conserva el período aplicado', async () => {
    await navigate('/app/analitica');
    await loadShell();
    const old = request('/analytics/resumen');
    const component = harness.routeDebugElement!.query(By.directive(AnalyticsDashboard));
    const instance = component.componentInstance as AnalyticsDashboard;
    instance.desdeDraft = '2026-09-01';
    instance.hastaDraft = '2026-09-30';
    await update();
    expect(
      [...dashboard().querySelectorAll('button')].find((button) =>
        button.textContent?.includes('Aplicar período'),
      )!.disabled,
    ).toBe(false);
    dashboard()
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await update();
    expect(old.cancelled).toBe(true);
    const current = request('/analytics/resumen');
    expect(current.request.params.get('desde')).toBe('2026-09-01');
    expect(current.request.params.get('hasta')).toBe('2026-09-30');
    current.flush(summary());
    await update();
    instance.desdeDraft = '2026-08-01';
    instance.hastaDraft = '2026-08-31';
    await update();
    await click(dashboard(), 'Actualizar');
    const refreshed = request('/analytics/resumen');
    expect(refreshed.request.urlWithParams).toBe(current.request.urlWithParams);
    refreshed.flush(summary());
    await update();
  });
});
