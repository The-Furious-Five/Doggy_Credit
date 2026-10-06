import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import {
  EvaluacionResumen,
  EvaluacionResumenBandaEnum,
  EvaluacionResumenDecisionEnum,
  EvaluacionResumenEstadoEnum,
  EvaluacionesService,
  PaginaEvaluaciones,
} from '../api';
import { AssessmentList } from './assessment-list';

const item = (i: number, estado = EvaluacionResumenEstadoEnum.RevisionManual): EvaluacionResumen => ({
  id: `ev-${i}`,
  solicitanteId: `sol-${i}`,
  estado,
  score: 700 - i,
  banda: EvaluacionResumenBandaEnum.B,
  montoSolicitado: '3000.00',
  plazoMeses: 12,
  creadoEn: '2026-10-05T12:00:00Z',
});
const page = (n: number, total = n): PaginaEvaluaciones => ({
  items: Array.from({ length: n }, (_, i) => item(i)),
  total,
});

describe('Listado de evaluaciones (C-09)', () => {
  const api = { listarEvaluaciones: vi.fn() };

  async function render(inputs: Record<string, unknown> = {}): Promise<ComponentFixture<AssessmentList>> {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: EvaluacionesService, useValue: api }],
    });
    const fixture = TestBed.createComponent(AssessmentList);
    for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  const text = (f: ComponentFixture<AssessmentList>) => (f.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => api.listarEvaluaciones.mockReset());

  it('Resumen: las más recientes del tenant con enlace al perfil del cliente', async () => {
    api.listarEvaluaciones.mockReturnValue(of(page(2)));
    const f = await render({ pageSize: 5 });

    const rows = f.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('/app/clientes/sol-0');
    expect(text(f)).toContain('Revisión manual');
    expect(text(f)).not.toContain('Monto');
    expect(f.nativeElement.querySelector('ds-pagination')).toBeNull();
    expect(api.listarEvaluaciones).toHaveBeenCalledWith(undefined, undefined, 1, 5);
  });

  it('Score del perfil: historial del cliente, paginado, con monto y plazo', async () => {
    api.listarEvaluaciones.mockReturnValue(of(page(10, 12)));
    const f = await render({ applicantId: 'sol-7', paginate: true });

    expect(api.listarEvaluaciones).toHaveBeenCalledWith('sol-7', undefined, 1, 10);
    expect(text(f)).toContain('3.000');
    expect(text(f)).toContain('12 m');
    expect(text(f)).toContain('1–10 de 12');
    expect(f.nativeElement.querySelector('tbody a')).toBeNull();

    f.componentInstance.page.set(2);
    await f.whenStable();
    expect(api.listarEvaluaciones).toHaveBeenLastCalledWith('sol-7', undefined, 2, 10);
  });

  it('muestra la decisión de cada evaluación con su etiqueta', async () => {
    api.listarEvaluaciones.mockReturnValue(
      of({
        items: [
          item(0, EvaluacionResumenEstadoEnum.Preaprobado),
          item(1, EvaluacionResumenEstadoEnum.Parcial),
          item(2, EvaluacionResumenEstadoEnum.Fallida),
        ],
        total: 3,
      }),
    );
    const f = await render();
    expect(text(f)).toContain('Preaprobado');
    expect(text(f)).toContain('Parcial');
    expect(text(f)).toContain('Fallida');
  });

  it('muestra la decisión de las políticas, incluida «Solicitar datos», y marca los parciales', async () => {
    api.listarEvaluaciones.mockReturnValue(
      of({
        items: [
          { ...item(0, EvaluacionResumenEstadoEnum.RevisionManual), decision: EvaluacionResumenDecisionEnum.SolicitarDatos },
          { ...item(1, EvaluacionResumenEstadoEnum.Parcial), decision: EvaluacionResumenDecisionEnum.RevisionManual },
        ],
        total: 2,
      }),
    );
    const f = await render();
    const badges = [...f.nativeElement.querySelectorAll('ds-badge')].map((b: Element) => b.textContent?.trim());
    expect(badges).toEqual(['Solicitar datos', 'Parcial']);
  });

  it('muestra un estado vacío distinto para el Resumen y para un cliente', async () => {
    api.listarEvaluaciones.mockReturnValue(of(page(0)));
    expect(text(await render())).toContain('Empieza con una nueva evaluación');

    TestBed.resetTestingModule();
    api.listarEvaluaciones.mockReturnValue(of(page(0)));
    expect(text(await render({ applicantId: 'sol-1' }))).toContain('Este cliente todavía no tiene evaluaciones');
  });

  it('muestra un error con reintento si la API falla', async () => {
    api.listarEvaluaciones.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const f = await render();
    expect(text(f)).toContain('No pudimos cargar las evaluaciones');

    api.listarEvaluaciones.mockReturnValue(of(page(1)));
    (f.nativeElement.querySelector('button') as HTMLButtonElement).click();
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });
});
