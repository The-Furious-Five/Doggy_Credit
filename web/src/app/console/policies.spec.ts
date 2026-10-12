import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Politica, PoliticaResultadoEnum, PoliticasService } from '../api';
import { Policies } from './policies';

const preapproval: Politica = {
  id: '11111111-1111-4111-8111-111111111111',
  codigo: 'PREAPROBACION_SCORE',
  nombre: 'Preaprobación por score',
  condicion: 'score ≥ 650 y resultado completo',
  resultado: PoliticaResultadoEnum.Preaprobado,
  activa: true,
};
const missingData: Politica = {
  id: '22222222-2222-4222-8222-222222222222',
  codigo: 'DATOS_INSUFICIENTES',
  nombre: 'Datos insuficientes',
  condicion: 'fuentes que respondieron < 2',
  resultado: PoliticaResultadoEnum.SolicitarDatos,
  activa: false,
};

describe('Políticas conectadas a la API (C-05)', () => {
  const api = { listarPoliticas: vi.fn(), actualizarPolitica: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [Policies],
      providers: [{ provide: PoliticasService, useValue: api }],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(Policies);
    fixture.detectChanges();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('muestra las reglas de la API con su condición, resultado y estado', () => {
    api.listarPoliticas.mockReturnValue(of([preapproval, missingData]));
    const { page } = render();

    expect(page.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(page.textContent).toContain('Preaprobación por score');
    expect(page.textContent).toContain('score ≥ 650 y resultado completo');
    expect(page.textContent).toContain('Preaprobar');
    expect(page.textContent).toContain('Solicitar datos');
    expect(page.textContent).toContain('Pausada');
  });

  it('nombra cada interruptor con su regla, no solo con el estado (E-09)', () => {
    api.listarPoliticas.mockReturnValue(of([preapproval, missingData]));
    const { page } = render();

    const names = [...page.querySelectorAll('[role="switch"]')].map(
      (control) => page.querySelector(`#${control.getAttribute('aria-labelledby')}`)?.textContent?.trim(),
    );
    expect(names).toEqual(['Activa: Preaprobación por score', 'Pausada: Datos insuficientes']);
  });

  it('no ofrece crear reglas (el editor está fuera de alcance)', () => {
    api.listarPoliticas.mockReturnValue(of([preapproval]));
    const { page } = render();
    expect(page.textContent).not.toContain('Nueva regla');
  });

  it('promete que nunca hay rechazo automático', () => {
    api.listarPoliticas.mockReturnValue(of([preapproval]));
    const { page } = render();
    expect(page.textContent).toContain('nunca rechazan sin intervención humana');
    expect(page.textContent).not.toMatch(/Rechazar/);
  });

  it('muestra un estado vacío si el tenant no tiene reglas', () => {
    api.listarPoliticas.mockReturnValue(of([]));
    const { page } = render();
    expect(page.textContent).toContain('Sin reglas');
  });

  it('muestra un error con reintento si la API falla', () => {
    api.listarPoliticas.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const { fixture, page } = render();
    expect(page.textContent).toContain('No pudimos cargar las políticas');

    api.listarPoliticas.mockReturnValue(of([preapproval]));
    (page.querySelector('.ds-alert button, ds-alert button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(api.listarPoliticas).toHaveBeenCalledTimes(2);
    expect(page.textContent).toContain('Preaprobación por score');
  });

  it('explica que solo el administrador puede ver las políticas (403)', () => {
    api.listarPoliticas.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const { page } = render();
    expect(page.textContent).toContain('Solo el administrador del tenant puede ver las políticas');
  });

  it('el interruptor activa o pausa la regla en el servidor', () => {
    api.listarPoliticas.mockReturnValue(of([missingData]));
    api.actualizarPolitica.mockReturnValue(of({ ...missingData, activa: true }));
    const { fixture } = render();

    fixture.componentInstance.setActive(missingData, true);

    expect(api.actualizarPolitica).toHaveBeenCalledWith(missingData.id, { activa: true });
    expect(fixture.componentInstance.rules()[0].activa).toBe(true);
    expect(fixture.componentInstance.saving()).toBeNull();
  });

  it('si el guardado falla, la regla vuelve a su estado anterior', () => {
    api.listarPoliticas.mockReturnValue(of([preapproval]));
    api.actualizarPolitica.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const { fixture } = render();

    fixture.componentInstance.setActive(preapproval, false);

    expect(fixture.componentInstance.rules()[0].activa).toBe(true);
    expect(fixture.componentInstance.saving()).toBeNull();
  });
});
