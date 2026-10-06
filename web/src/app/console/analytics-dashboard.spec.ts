import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgForm } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import {
  AnalyticsModeloEstadoEnum,
  AnalyticsNecesidadCategoriaEnum,
  AnalyticsProductoTipoEnum,
  AnalyticsResumen,
  AnalyticsService,
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

describe('KPIs de Resumen y Analítica (E-02)', () => {
  const api = { obtenerResumenAnalytics: vi.fn() };
  const sensitive = { amount: vi.fn((value: string) => value) };

  beforeEach(() => {
    api.obtenerResumenAnalytics.mockReset().mockReturnValue(of(summary()));
    sensitive.amount.mockReset().mockImplementation((value: string) => value);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AnalyticsService, useValue: api },
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
    empty.pro!.tiempoDecisionMedianaMs = null;
    empty.pro!.modelos = [];
    empty.pro!.recomendacionesPorProducto = [];
    api.obtenerResumenAnalytics.mockReturnValue(of(empty));
    const f = await render();
    expect(stats(f)).toContain('—');
    expect(text(f)).toContain('Sin evaluaciones en este período');
    expect(text(f)).toContain('Sin recomendaciones');
    expect(text(f)).toContain('Sin monitoreo de modelos');
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
});
