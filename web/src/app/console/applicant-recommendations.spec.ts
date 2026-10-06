import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import {
  EvaluacionBandaEnum,
  EvaluacionEstadoEnum,
  EvaluacionResumenBandaEnum,
  EvaluacionResumenEstadoEnum,
  EvaluacionesService,
  RecomendacionMonedaEnum,
  RecomendacionTipoProductoEnum,
} from '../api';
import { ApplicantRecommendations } from './applicant-recommendations';

const summary = {
  id: 'ev-1',
  solicitanteId: 'sol-1',
  estado: EvaluacionResumenEstadoEnum.RevisionManual,
  score: 700,
  banda: EvaluacionResumenBandaEnum.B,
  creadoEn: '2026-10-05T12:00:00Z',
};

describe('Recomendaciones del perfil (C-06)', () => {
  const api = { listarEvaluaciones: vi.fn(), obtenerEvaluacion: vi.fn() };

  async function render(): Promise<ComponentFixture<ApplicantRecommendations>> {
    TestBed.configureTestingModule({ providers: [{ provide: EvaluacionesService, useValue: api }] });
    const fixture = TestBed.createComponent(ApplicantRecommendations);
    fixture.componentRef.setInput('applicantId', 'sol-1');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  const text = (f: ComponentFixture<ApplicantRecommendations>) => (f.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => vi.resetAllMocks());

  it('muestra las recomendaciones de la evaluación más reciente del cliente', async () => {
    api.listarEvaluaciones.mockReturnValue(of({ items: [summary], total: 3 }));
    api.obtenerEvaluacion.mockReturnValue(
      of({
        id: 'ev-1',
        estado: EvaluacionEstadoEnum.RevisionManual,
        banda: EvaluacionBandaEnum.B,
        recomendaciones: [
          {
            tipoProducto: RecomendacionTipoProductoEnum.Microcredito,
            moneda: RecomendacionMonedaEnum.Bob,
            montoSugerido: '3000.00',
            razon: 'Banda B cumple la mínima C',
          },
        ],
      }),
    );
    const f = await render();

    expect(api.listarEvaluaciones).toHaveBeenCalledWith('sol-1', undefined, 1, 1);
    expect(api.obtenerEvaluacion).toHaveBeenCalledWith('ev-1');
    expect(text(f)).toContain('Microcrédito');
    expect(text(f)).toContain('Bs 3.000');
    expect(text(f)).toContain('no una aprobación');
  });

  it('si la evaluación no trae recomendaciones (FREE o parcial) lo explica', async () => {
    api.listarEvaluaciones.mockReturnValue(of({ items: [summary], total: 1 }));
    api.obtenerEvaluacion.mockReturnValue(of({ id: 'ev-1', estado: EvaluacionEstadoEnum.Parcial }));
    const f = await render();
    expect(text(f)).toContain('disponibles en el plan Pro');
  });

  it('sin evaluaciones muestra un estado vacío y no consulta ninguna evaluación', async () => {
    api.listarEvaluaciones.mockReturnValue(of({ items: [], total: 0 }));
    const f = await render();
    expect(text(f)).toContain('Sin recomendaciones');
    expect(api.obtenerEvaluacion).not.toHaveBeenCalled();
  });

  it('muestra un error con reintento si la API falla', async () => {
    api.listarEvaluaciones.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const f = await render();
    expect(text(f)).toContain('No pudimos cargar las recomendaciones');

    api.listarEvaluaciones.mockReturnValue(of({ items: [], total: 0 }));
    (f.nativeElement.querySelector('button') as HTMLButtonElement).click();
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(text(f)).toContain('Sin recomendaciones');
  });
});
