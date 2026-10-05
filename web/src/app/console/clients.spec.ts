import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PaginaSolicitantes, Segmento, SolicitanteMonedaEnum, SolicitantesService } from '../api';
import { Clients } from './clients';
import { SensitiveDataService } from './sensitive-data.service';

const page = (n: number, total = n): PaginaSolicitantes => ({
  total,
  items: Array.from({ length: n }, (_, i) => ({
    id: `id-${i}`,
    documento: `CI-DEMO-000100${i}`,
    actividad: 'Comercio',
    antiguedadActividadMeses: 24,
    ingresosMensuales: '9800.00',
    moneda: SolicitanteMonedaEnum.Bob,
    destinoCredito: 'Capital de trabajo',
    segmento: Segmento.Pyme,
    creadoEn: '2026-10-05T12:00:00Z',
  })),
});

describe('Clientes (B-09)', () => {
  const api = { listarSolicitantes: vi.fn() };

  async function render(): Promise<ComponentFixture<Clients>> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SolicitantesService, useValue: api },
        { provide: SensitiveDataService, useValue: { document: (v: string) => v, amount: (v: string) => v } },
      ],
    });
    const fixture = TestBed.createComponent(Clients);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  const text = (f: ComponentFixture<Clients>) => (f.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => api.listarSolicitantes.mockReset());

  it('lista los clientes de la API con enlace al perfil y paginación', async () => {
    api.listarSolicitantes.mockReturnValue(of(page(2, 12)));
    const f = await render();
    const rows = f.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('/id-0');
    expect(text(f)).toContain('PYME');
    expect(text(f)).toContain('Bs 9.800');
    expect(text(f)).toContain('1–10 de 12');
    expect(api.listarSolicitantes).toHaveBeenCalledWith(1, 10, undefined, undefined);
  });

  it('busca por documento en la cabecera (no en la URL) y filtra por segmento', async () => {
    api.listarSolicitantes.mockReturnValue(of(page(1)));
    const f = await render();
    f.componentInstance.documentDraft = ' CI-DEMO-0001001 ';
    f.componentInstance.search();
    f.componentInstance.setSegment(Segmento.Pyme);
    f.detectChanges();
    await f.whenStable();
    expect(api.listarSolicitantes).toHaveBeenLastCalledWith(1, 10, Segmento.Pyme, 'CI-DEMO-0001001');
  });

  it('muestra el error con reintento', async () => {
    api.listarSolicitantes.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const f = await render();
    expect(text(f)).toContain('No pudimos cargar los clientes');
    api.listarSolicitantes.mockReturnValue(of(page(1)));
    const retry = [...f.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Reintentar'),
    ) as HTMLButtonElement;
    retry.click();
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(f.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('muestra el estado vacío', async () => {
    api.listarSolicitantes.mockReturnValue(of(page(0)));
    const f = await render();
    expect(text(f)).toContain('Todavía no hay clientes');
  });
});
