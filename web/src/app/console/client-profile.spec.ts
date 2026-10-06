import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import {
  EvaluacionesService,
  Consentimiento,
  ConsentimientoEstadoEnum,
  ConsentimientosService,
  PerfilFinanciero360FuentesInnerEstadoEnum as Estado,
  PerfilFinanciero360FuentesInnerFuenteEnum as Fuente,
  PerfilFinanciero360OrigenIngresoEnum,
  SolicitanteDetalle,
  SolicitanteDetalleMonedaEnum,
  SolicitantesService,
  DineroCurrencyEnum,
  Evaluacion,
  EvaluacionResumenBandaEnum,
  EvaluacionResumenEstadoEnum,
  RecomendacionProductoTipoEnum,
} from '../api';
import { ClientProfile } from './client-profile';
import { SensitiveDataService } from './sensitive-data.service';

const bob = (amount: string) => ({ amount, currency: DineroCurrencyEnum.Bob });

const detail = (withProfile = true): SolicitanteDetalle => ({
  id: 'a-1',
  documento: 'CI-DEMO-0001001',
  actividad: 'Comercio',
  antiguedadActividadMeses: 24,
  ingresosMensuales: '5000.00',
  moneda: SolicitanteDetalleMonedaEnum.Bob,
  destinoCredito: 'Capital',
  creadoEn: '2026-10-01T12:00:00Z',
  perfil: withProfile
    ? {
        id: 'p-1',
        hash: 'h',
        creadoEn: '2026-10-05T12:00:00Z',
        ingresoMensual: bob('9500.00'),
        origenIngreso: PerfilFinanciero360OrigenIngresoEnum.Banco,
        deudaVigente: bob('8580.00'),
        obligaciones: 1,
        cuotaMensual: bob('1930.00'),
        endeudamientoPorcentaje: '20.3',
        capacidadPago: bob('3000.00'),
        antiguedadSistemaFinancieroMeses: 92,
        fuentes: [
          { fuente: Fuente.Buro, estado: Estado.Completa, consultadaEn: '2026-10-05T12:00:00Z', sha256: 'abcdef0123456789' },
          { fuente: Fuente.Banco, estado: Estado.NoDisponible },
          { fuente: Fuente.Wallet, estado: Estado.NoConsultada },
        ],
      }
    : undefined,
});

const consent = (estado: ConsentimientoEstadoEnum): Consentimiento =>
  ({ id: `c-${estado}`, estado }) as Consentimiento;

describe('Perfil del cliente (B-09)', () => {
  const applicants = { obtenerSolicitante: vi.fn() };
  const consents = { listarConsentimientos: vi.fn(), revocarConsentimiento: vi.fn() };
  const evaluations = { listarEvaluaciones: vi.fn(), obtenerEvaluacion: vi.fn() };

  async function render(waitForResponse = true): Promise<ComponentFixture<ClientProfile>> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SolicitantesService, useValue: applicants },
        { provide: ConsentimientosService, useValue: consents },
        { provide: EvaluacionesService, useValue: evaluations },
        { provide: SensitiveDataService, useValue: { document: (v: string) => v, amount: (v: string) => v } },
      ],
    });
    const fixture = TestBed.createComponent(ClientProfile);
    fixture.componentRef.setInput('id', 'a-1');
    fixture.detectChanges();
    if (waitForResponse) await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  const text = (f: ComponentFixture<ClientProfile>) => (f.nativeElement as HTMLElement).textContent ?? '';

  // jsdom no implementa el diálogo modal nativo. Se restaura al final porque los
  // specs comparten el mismo jsdom: si no, otros specs heredan estos stubs.
  const { showModal, close } = HTMLDialogElement.prototype;

  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = vi.fn();
    HTMLDialogElement.prototype.close = vi.fn();
  });

  afterAll(() => {
    HTMLDialogElement.prototype.showModal = showModal;
    HTMLDialogElement.prototype.close = close;
  });

  beforeEach(() => {
    vi.resetAllMocks();
    consents.listarConsentimientos.mockReturnValue(of([consent(ConsentimientoEstadoEnum.Activo)]));
    evaluations.listarEvaluaciones.mockReturnValue(of({ items: [], total: 0 }));
  });

  it('muestra el resumen y el linaje por fuente del Perfil 360', async () => {
    applicants.obtenerSolicitante.mockReturnValue(of(detail()));
    const f = await render();
    expect(text(f)).toContain('CI-DEMO-0001001');
    expect(text(f)).toContain('Consentimiento vigente');
    expect(text(f)).toContain('Bs 9.500');
    expect(text(f)).toContain('Promedio de 6 meses en el banco');
    expect(text(f)).toContain('1 obligación');
    expect(text(f)).toContain('20.3 %');
    expect(text(f)).toContain('7 años');
    // Pestaña Fuentes: huella abreviada y estado escrito, no solo color.
    expect(text(f)).toContain('abcdef012345');
    expect(text(f)).toContain('No disponible');
    expect(text(f)).toContain('No consultada');
    expect(text(f)).not.toContain('Exportar');
  });

  it('sin perfil consolidado invita a evaluar', async () => {
    applicants.obtenerSolicitante.mockReturnValue(of(detail(false)));
    const f = await render();
    expect(text(f)).toContain('Sin perfil consolidado');
  });

  it('un cliente de otro tenant se muestra como no encontrado', async () => {
    applicants.obtenerSolicitante.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const f = await render();
    expect(text(f)).toContain('Cliente no encontrado');
    expect(evaluations.listarEvaluaciones).not.toHaveBeenCalled();
    expect(evaluations.obtenerEvaluacion).not.toHaveBeenCalled();
  });

  function latest(result: Evaluacion): void {
    evaluations.listarEvaluaciones.mockReturnValue(of({
      items: [{ id: 'ev-latest', solicitanteId: 'a-1', estado: EvaluacionResumenEstadoEnum.RevisionManual,
        score: 800, banda: EvaluacionResumenBandaEnum.A, creadoEn: '2026-10-06T12:00:00Z' }], total: 1,
    }));
    evaluations.obtenerEvaluacion.mockReturnValue(of(result));
  }

  it('muestra el producto y monto reales de la última evaluación de este cliente', async () => {
    applicants.obtenerSolicitante.mockReturnValue(of(detail()));
    latest({ recomendacionesDisponibles: true, recomendaciones: [
      { tipo: RecomendacionProductoTipoEnum.Verde, montoRecomendado: '4123.25' },
    ] });
    const f = await render();
    expect(evaluations.listarEvaluaciones).toHaveBeenCalledWith('a-1', undefined, 1, 1);
    expect(evaluations.obtenerEvaluacion).toHaveBeenCalledWith('ev-latest');
    expect(text(f)).toContain('Crédito verde');
    expect(text(f)).toContain('4.123,25');
    expect(text(f)).not.toContain('Sin recomendaciones');
  });

  it('FREE muestra el permiso devuelto por la API, sin deducirlo localmente', async () => {
    applicants.obtenerSolicitante.mockReturnValue(of(detail()));
    latest({ recomendacionesDisponibles: false, recomendaciones: [] });
    const f = await render();
    expect(f.nativeElement.querySelector('ds-alert[role="status"]')?.textContent)
      .toContain('Recomendaciones disponibles con Pro');
    expect(text(f)).not.toContain('Sin recomendaciones');
    expect(text(f)).not.toContain('Monto recomendado');
  });

  it('PRO sin producto elegible muestra vacío y conserva la opción de reevaluar', async () => {
    applicants.obtenerSolicitante.mockReturnValue(of(detail()));
    latest({ recomendacionesDisponibles: true, recomendaciones: [] });
    const f = await render();
    expect(text(f)).toContain('Sin recomendaciones');
    expect(text(f)).not.toContain('Recomendaciones disponibles con Pro');
    expect(text(f)).toContain('Evaluar ahora');
  });

  it('sin evaluaciones muestra vacío sin pedir un detalle inexistente', async () => {
    applicants.obtenerSolicitante.mockReturnValue(of(detail(false)));
    const f = await render();
    expect(text(f)).toContain('Sin recomendaciones');
    expect(evaluations.obtenerEvaluacion).not.toHaveBeenCalled();
  });

  it('anuncia la carga mientras espera el detalle de la evaluación', async () => {
    applicants.obtenerSolicitante.mockReturnValue(of(detail()));
    latest({});
    const response = new Subject<Evaluacion>();
    evaluations.obtenerEvaluacion.mockReturnValue(response);
    const f = await render(false);
    expect(f.nativeElement.querySelector('ds-spinner[label="Cargando recomendaciones…"]')).not.toBeNull();
    expect(text(f)).not.toContain('Sin recomendaciones');
    response.next({ recomendacionesDisponibles: true, recomendaciones: [] });
    response.complete();
    await f.whenStable();
    f.detectChanges();
    expect(text(f)).toContain('Sin recomendaciones');
  });

  it('permite reintentar un fallo de carga sin mostrar una restricción falsa', async () => {
    applicants.obtenerSolicitante.mockReturnValue(of(detail()));
    latest({});
    evaluations.obtenerEvaluacion.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const f = await render();
    expect(text(f)).toContain('No pudimos cargar las recomendaciones');
    expect(text(f)).not.toContain('Recomendaciones disponibles con Pro');
    evaluations.obtenerEvaluacion.mockReturnValue(of({ recomendacionesDisponibles: true, recomendaciones: [
      { tipo: RecomendacionProductoTipoEnum.Microcredito, montoRecomendado: '3000.00' },
    ] }));
    const button = [...f.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Reintentar recomendaciones')) as HTMLButtonElement;
    button.click();
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(text(f)).toContain('Microcrédito');
    expect(text(f)).not.toContain('No pudimos cargar las recomendaciones');
  });

  it('al cambiar de cliente consulta su evaluación y retira los productos anteriores', async () => {
    applicants.obtenerSolicitante.mockReturnValue(of(detail()));
    latest({ recomendacionesDisponibles: true, recomendaciones: [
      { tipo: RecomendacionProductoTipoEnum.Verde, montoRecomendado: '4123.25' },
    ] });
    const f = await render();
    expect(text(f)).toContain('Crédito verde');
    applicants.obtenerSolicitante.mockReturnValue(of({ ...detail(), id: 'a-2' }));
    evaluations.listarEvaluaciones.mockReturnValue(of({ items: [{ id: 'ev-second', solicitanteId: 'a-2',
      estado: EvaluacionResumenEstadoEnum.RevisionManual, score: 700, banda: EvaluacionResumenBandaEnum.B,
      creadoEn: '2026-10-06T12:00:00Z' }], total: 1 }));
    evaluations.obtenerEvaluacion.mockReturnValue(of({ recomendacionesDisponibles: true, recomendaciones: [
      { tipo: RecomendacionProductoTipoEnum.Microcredito, montoRecomendado: '2000.00' },
    ] }));
    f.componentRef.setInput('id', 'a-2');
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(evaluations.listarEvaluaciones).toHaveBeenCalledWith('a-2', undefined, 1, 1);
    expect(evaluations.obtenerEvaluacion).toHaveBeenCalledWith('ev-second');
    expect(text(f)).toContain('Microcrédito');
    expect(text(f)).not.toContain('Crédito verde');
    expect(text(f)).not.toContain('4.123,25');
  });

  it('revoca los consentimientos vigentes solo tras confirmar', async () => {
    applicants.obtenerSolicitante.mockReturnValue(of(detail()));
    consents.revocarConsentimiento.mockReturnValue(of(consent(ConsentimientoEstadoEnum.Revocado)));
    const f = await render();
    expect(consents.revocarConsentimiento).not.toHaveBeenCalled();

    consents.listarConsentimientos.mockReturnValue(of([consent(ConsentimientoEstadoEnum.Revocado)]));
    f.componentInstance.revoke();
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(consents.revocarConsentimiento).toHaveBeenCalledWith('c-ACTIVO');
    expect(text(f)).toContain('Sin consentimiento vigente');
    expect(f.componentInstance.actions()[0].disabled).toBe(true);
  });
});
