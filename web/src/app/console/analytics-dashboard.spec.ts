import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgForm } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import {
  AnalyticsAceptacionProductoTipoEnum,
  AnalyticsBandaBandaEnum,
  AnalyticsModeloEstadoEnum,
  AnalyticsNecesidadCategoriaEnum,
  AnalyticsProductoTipoEnum,
  AnalyticsResumen,
  AnalyticsService,
  EvaluacionesService,
  EvaluacionResumenEstadoEnum,
  EvaluacionResumenBandaEnum,
} from '../api';
import { Analytics } from './analytics';
import { AnalyticsDashboard } from './analytics-dashboard';
import { Overview } from './overview';
import { SensitiveDataService } from './sensitive-data.service';

const summary = (premium = false): AnalyticsResumen => ({
  desde: '2026-10-01',
  hasta: '2026-10-31',
  plan: premium ? 'PRO' : 'FREE',
  moneda: 'BOB',
  analiticaAvanzada: premium,
  evaluaciones: 20,
  scorePromedio: 710,
  integracionesActivas: 2,
  porcentajeConRecomendacion: 60,
  necesidades: [
    { categoria: AnalyticsNecesidadCategoriaEnum.CapitalTrabajo, cantidad: 15, porcentaje: 75 },
  ],
  ...(premium
    ? {
        pro: {
          conversionPorcentaje: 30,
          tiempoDecisionMedianaMs: 250,
          montoRecomendado: '12000.00',
          recomendacionesPorProducto: [
            {
              tipo: AnalyticsProductoTipoEnum.Microcredito,
              cantidad: 12,
              porcentaje: 100,
              montoRecomendado: '12000.00',
            },
          ],
          modelos: [
            {
              version: 'scoring-v2',
              psi: 0.3,
              estado: AnalyticsModeloEstadoEnum.Alerta,
              desde: '2026-10-05T04:00:00Z',
              hasta: '2026-10-12T04:00:00Z',
              muestrasActuales: 50,
              muestrasBase: 100,
            },
          ],
        },
      }
    : {}),
});

describe('KPIs de Resumen y Analítica (E-02 / E-05)', () => {
  const api = { obtenerResumenAnalytics: vi.fn() };
  const assessments = { listarEvaluaciones: vi.fn() };
  const sensitive = { amount: vi.fn((value: string) => value) };

  beforeEach(() => {
    api.obtenerResumenAnalytics.mockReset().mockReturnValue(of(summary()));
    assessments.listarEvaluaciones.mockReset().mockReturnValue(of({ items: [], total: 0 }));
    sensitive.amount.mockReset().mockImplementation((value: string) => value);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AnalyticsService, useValue: api },
        { provide: EvaluacionesService, useValue: assessments },
        { provide: SensitiveDataService, useValue: sensitive },
      ],
    });
  });

  async function render(): Promise<ComponentFixture<AnalyticsDashboard>> {
    const f = TestBed.createComponent(AnalyticsDashboard);
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    return f;
  }
  const text = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent ?? '';
  const stats = (f: ComponentFixture<unknown>) =>
    [...f.nativeElement.querySelectorAll('ds-stat')]
      .map((stat: HTMLElement) => stat.textContent)
      .join(' ');
  const form = (f: ComponentFixture<AnalyticsDashboard>) =>
    f.debugElement.query(By.directive(NgForm)).injector.get(NgForm);

  it.each([Overview, Analytics])('conecta la vista %s al endpoint compartido', async (view) => {
    const f = TestBed.createComponent(view);
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(api.obtenerResumenAnalytics).toHaveBeenCalledTimes(1);
    expect(stats(f)).toContain('710');
    expect(stats(f)).toContain('60 %');
    expect(text(f)).toContain('Capital de trabajo · 15');
    expect(text(f)).not.toContain('Mora a 90 días');
  });

  it('muestra los indicadores comunes de Gratis y oculta los avanzados', async () => {
    const f = await render();
    expect(stats(f)).toContain('Evaluaciones');
    expect(stats(f)).toContain('Integraciones activas');
    expect(stats(f)).not.toContain('Conversión');
    expect(f.nativeElement.querySelector('table')).toBeNull();
    expect(text(f)).toContain('Plan Gratis');
    expect(text(f)).toContain('Analítica avanzada disponible con Pro');
  });

  it('conserva las evaluaciones recientes de main junto con los KPIs de E-02', async () => {
    assessments.listarEvaluaciones.mockReturnValue(
      of({
        total: 1,
        items: [
          {
            id: 'evaluacion-main',
            solicitanteId: 'cliente-main',
            estado: EvaluacionResumenEstadoEnum.Preaprobado,
            banda: EvaluacionResumenBandaEnum.A,
            score: 850,
            creadoEn: '2026-10-06T12:00:00Z',
          },
        ],
      }),
    );
    const f = TestBed.createComponent(Overview);
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(stats(f)).toContain('710');
    expect(text(f)).toContain('Evaluaciones recientes');
    expect(text(f)).toContain('Preaprobado');
    expect(f.nativeElement.querySelector('tbody tr').textContent).toContain('850');
    expect(f.nativeElement.querySelector('tbody a').getAttribute('href')).toBe(
      '/app/clientes/cliente-main',
    );
    expect(assessments.listarEvaluaciones).toHaveBeenCalledWith(undefined, undefined, 1, 5);
  });

  it('muestra Pro con los resultados reales de D-08', async () => {
    api.obtenerResumenAnalytics.mockReturnValue(of(summary(true)));
    const f = await render();
    expect(stats(f)).toContain('30 %');
    expect(stats(f)).toContain('Bs 12.000');
    expect(stats(f)).toContain('0,25 s');
    expect(text(f)).toContain('Microcrédito · 12');
    expect(text(f)).toContain('scoring-v2');
    expect(text(f)).toContain('Alerta');
    expect(text(f)).toContain('50 / 100');
    expect(text(f)).not.toContain('Vista previa');
  });

  it('respeta la preferencia existente de enmascarar montos', async () => {
    sensitive.amount.mockReturnValue('••••••');
    api.obtenerResumenAnalytics.mockReturnValue(of(summary(true)));
    const f = await render();
    expect(stats(f)).toContain('••••••');
    expect(text(f)).not.toContain('12.000');
  });

  it('conserva null como desconocido y muestra estados vacíos', async () => {
    const empty = summary(true);
    empty.evaluaciones = 0;
    empty.scorePromedio = null;
    empty.necesidades = [];
    empty.pro!.tiempoDecisionMedianaMs = null;
    empty.pro!.modelos = [];
    empty.pro!.recomendacionesPorProducto = [];
    api.obtenerResumenAnalytics.mockReturnValue(of(empty));
    const f = await render();
    expect(stats(f)).toContain('—');
    expect(text(f)).toContain('Sin evaluaciones en este período');
    expect(text(f)).toContain('Sin necesidades detectadas');
    expect(text(f)).toContain('Sin recomendaciones');
    expect(text(f)).toContain('Sin monitoreo de modelos');
  });

  it.each([
    { needs: [] },
    {
      needs: [
        { categoria: AnalyticsNecesidadCategoriaEnum.CapitalTrabajo, cantidad: 0, porcentaje: 0 },
      ],
    },
  ])('explica la ausencia de necesidades aunque existan evaluaciones', async ({ needs }) => {
    api.obtenerResumenAnalytics.mockReturnValue(of({ ...summary(), necesidades: needs }));
    const f = await render();
    expect(stats(f)).toContain('20');
    expect(text(f)).toContain('Sin necesidades detectadas');
    expect(text(f)).not.toContain('Sin evaluaciones en este período');
    expect(f.nativeElement.querySelector('ds-progress')).toBeNull();
  });

  it('conserva los KPIs básicos y reintenta si faltan datos avanzados de Pro', async () => {
    const premium = summary(true);
    delete premium.pro;
    api.obtenerResumenAnalytics.mockReturnValue(of(premium));
    const f = await render();
    expect(stats(f)).toContain('710');
    expect(text(f)).toContain('Plan Pro');
    expect(text(f)).toContain('Indicadores avanzados no disponibles');
    expect(text(f)).not.toContain('Analítica avanzada disponible con Pro');
    api.obtenerResumenAnalytics.mockReturnValue(of(summary(true)));
    const retry = [...f.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.includes('Reintentar'),
    );
    retry.click();
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(stats(f)).toContain('Conversión');
    expect(text(f)).not.toContain('Indicadores avanzados no disponibles');
    expect(api.obtenerResumenAnalytics.mock.calls[1]).toEqual(
      api.obtenerResumenAnalytics.mock.calls[0],
    );
  });

  it('aplica el intervalo y evita consultas con fechas invertidas', async () => {
    const f = await render();
    const dashboard = f.componentInstance;
    dashboard.desdeDraft = '2026-09-01';
    dashboard.hastaDraft = '2026-09-30';
    f.detectChanges();
    await f.whenStable();
    dashboard.apply(form(f));
    f.detectChanges();
    await f.whenStable();
    expect(api.obtenerResumenAnalytics).toHaveBeenLastCalledWith('2026-09-01', '2026-09-30');
    dashboard.desdeDraft = '2026-10-31';
    dashboard.hastaDraft = '2026-10-01';
    dashboard.apply(form(f));
    f.detectChanges();
    expect(api.obtenerResumenAnalytics).toHaveBeenCalledTimes(2);
    expect(text(f)).toContain('Desde debe ser anterior o igual a hasta.');
  });

  it('muestra carga y permite reintentar un error conservando las fechas', async () => {
    const pending = new Subject<AnalyticsResumen>();
    api.obtenerResumenAnalytics.mockReturnValue(pending);
    const f = TestBed.createComponent(AnalyticsDashboard);
    f.detectChanges();
    TestBed.tick();
    f.detectChanges();
    expect(text(f)).toContain('Cargando indicadores');
    pending.error(new HttpErrorResponse({ status: 503 }));
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(text(f)).toContain('No pudimos cargar los indicadores');
    api.obtenerResumenAnalytics.mockReturnValue(of(summary()));
    const retry = [...f.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.includes('Reintentar'),
    );
    retry.click();
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(stats(f)).toContain('710');
    expect(api.obtenerResumenAnalytics.mock.calls[1]).toEqual(
      api.obtenerResumenAnalytics.mock.calls[0],
    );
  });

  describe('KPIs de cartera (D-13 / RF-ANA-02)', () => {
    const portfolio = (): AnalyticsResumen => {
      const data = summary(true);
      data.pro = {
        ...data.pro!,
        distribucionBandas: [
          { banda: AnalyticsBandaBandaEnum.A, cantidad: 5, porcentaje: 25 },
          { banda: AnalyticsBandaBandaEnum.B, cantidad: 8, porcentaje: 40 },
          { banda: AnalyticsBandaBandaEnum.C, cantidad: 4, porcentaje: 20 },
          { banda: AnalyticsBandaBandaEnum.D, cantidad: 2, porcentaje: 10 },
          { banda: AnalyticsBandaBandaEnum.E, cantidad: 1, porcentaje: 5 },
        ],
        tasaOverride: { overrides: 3, evaluaciones: 20, porcentaje: 15 },
        aceptacionPorProducto: [
          {
            tipo: AnalyticsAceptacionProductoTipoEnum.Microcredito,
            recomendados: 10,
            aceptados: 4,
            porcentaje: 40,
          },
          {
            tipo: AnalyticsAceptacionProductoTipoEnum.Verde,
            recomendados: 0,
            aceptados: 0,
            porcentaje: 0,
          },
          {
            tipo: AnalyticsAceptacionProductoTipoEnum.Pyme,
            recomendados: 2,
            aceptados: 1,
            porcentaje: 50,
          },
        ],
      };
      return data;
    };
    const bars = (f: ComponentFixture<unknown>) =>
      [...f.nativeElement.querySelectorAll('ds-progress')].map((bar: HTMLElement) =>
        bar.textContent?.replace(/\s+/g, ' ').trim(),
      );

    it('muestra las cinco bandas, la tasa de override y la aceptación por producto en Pro', async () => {
      api.obtenerResumenAnalytics.mockReturnValue(of(portfolio()));
      const f = await render();
      expect(stats(f)).toContain('Tasa de override');
      expect(stats(f)).toContain('15 %');
      expect(stats(f)).toContain('3 casos resueltos en la bandeja de 20 evaluaciones');
      expect(text(f)).toContain('Distribución por banda');
      for (const label of [
        'Banda A · 5',
        'Banda B · 8',
        'Banda C · 4',
        'Banda D · 2',
        'Banda E · 1',
      ]) {
        expect(bars(f).some((bar) => bar?.includes(label))).toBe(true);
      }
      expect(text(f)).toContain('Aceptación por producto');
      expect(text(f)).toContain('Microcrédito · 4 de 10');
      expect(text(f)).toContain('Crédito PYME · 1 de 2');
      expect(text(f)).toContain('No mide desembolsos');
    });

    it('explica un período sin casos en vez de dibujar barras en cero', async () => {
      const empty = portfolio();
      empty.pro!.distribucionBandas = empty.pro!.distribucionBandas!.map((band) => ({
        ...band,
        cantidad: 0,
        porcentaje: 0,
      }));
      empty.pro!.aceptacionPorProducto = empty.pro!.aceptacionPorProducto!.map((product) => ({
        ...product,
        recomendados: 0,
        aceptados: 0,
        porcentaje: 0,
      }));
      empty.pro!.tasaOverride = { overrides: 0, evaluaciones: 0, porcentaje: 0 };
      api.obtenerResumenAnalytics.mockReturnValue(of(empty));
      const f = await render();
      expect(text(f)).toContain('Sin evaluaciones en este período');
      expect(text(f)).toContain('Sin recomendaciones en este período');
      expect(bars(f).some((bar) => bar?.startsWith('Banda'))).toBe(false);
      expect(stats(f)).toContain('0 casos resueltos en la bandeja de 0 evaluaciones');
    });

    it('no muestra los KPIs de cartera si el servidor todavía no los envía', async () => {
      api.obtenerResumenAnalytics.mockReturnValue(of(summary(true)));
      const f = await render();
      expect(text(f)).not.toContain('Distribución por banda');
      expect(text(f)).not.toContain('Aceptación por producto');
      expect(stats(f)).not.toContain('Tasa de override');
      expect(text(f)).toContain('Recomendaciones por producto');
    });

    it('en Gratis la invitación a Pro menciona estos KPIs, sin error', async () => {
      const f = await render();
      expect(text(f)).toContain('Analítica avanzada disponible con Pro');
      expect(text(f)).toContain('la tasa de override');
      expect(text(f)).toContain('la distribución por banda');
      expect(stats(f)).not.toContain('Tasa de override');
      expect(text(f)).not.toContain('No pudimos cargar');
    });
  });
});
