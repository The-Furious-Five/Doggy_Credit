import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
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

  async function render(): Promise<ComponentFixture<ClientProfile>> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SolicitantesService, useValue: applicants },
        { provide: ConsentimientosService, useValue: consents },
        // C-09: la pestaña Score lista el historial de evaluaciones.
        { provide: EvaluacionesService, useValue: { listarEvaluaciones: () => of({ items: [], total: 0 }) } },
        { provide: SensitiveDataService, useValue: { document: (v: string) => v, amount: (v: string) => v } },
      ],
    });
    const fixture = TestBed.createComponent(ClientProfile);
    fixture.componentRef.setInput('id', 'a-1');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  const text = (f: ComponentFixture<ClientProfile>) => (f.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    vi.resetAllMocks();
    // jsdom no implementa el diálogo modal nativo.
    HTMLDialogElement.prototype.showModal ??= vi.fn();
    HTMLDialogElement.prototype.close ??= vi.fn();
    consents.listarConsentimientos.mockReturnValue(of([consent(ConsentimientoEstadoEnum.Activo)]));
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
