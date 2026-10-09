import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import {
  ConsentimientosService,
  Evaluacion,
  EvaluacionBandaEnum,
  EvaluacionDecisionEnum,
  EvaluacionEstadoEnum,
  EvaluacionesService,
  FuenteIntegracion,
  IntegracionEstadoEnum,
  IntegracionesService,
  SolicitantesService,
} from '../api';
import { AuthSession, AuthSessionService } from '../auth/session.service';
import { Evaluation } from './evaluation';
import { TenantUsageService } from './tenant-usage.service';

const APPLICANT_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const CONSENT_ID = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';

const result = (overrides: Partial<Evaluacion> = {}): Evaluacion => ({
  id: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
  estado: EvaluacionEstadoEnum.Preaprobado,
  decision: EvaluacionDecisionEnum.Preaprobado,
  score: 820,
  banda: EvaluacionBandaEnum.A,
  razones: ['SIN_MORA_12M', 'ACTIVIDAD_MAYOR_24_MESES', 'HISTORIAL_MAYOR_24_MESES', 'PLAZO_HASTA_12_MESES'],
  modelVersion: 'basic-v1',
  simulado: true,
  ...overrides,
});
const problem = (status: number, code: string) =>
  new HttpErrorResponse({ status, error: { code, title: code } });

describe('Nueva evaluación conectada a la API (C-07)', () => {
  const applicants = { listarSolicitantes: vi.fn(), crearSolicitante: vi.fn(), obtenerSolicitante: vi.fn() };
  const consents = { crearConsentimiento: vi.fn() };
  const integrations = { listarIntegraciones: vi.fn() };
  const assessments = { crearEvaluacion: vi.fn() };
  const usage = { refresh: vi.fn() };
  const session = signal<AuthSession>({ isAuthenticated: true, name: 'Analista', tenantId: 't', roles: ['ANALYST'] });

  beforeEach(() => {
    vi.resetAllMocks();
    session.set({ isAuthenticated: true, name: 'Analista', tenantId: 't', roles: ['ANALYST'] });
    applicants.listarSolicitantes.mockReturnValue(of({ items: [], total: 0 }));
    applicants.crearSolicitante.mockReturnValue(of({ id: APPLICANT_ID }));
    applicants.obtenerSolicitante.mockReturnValue(
      of({ id: APPLICANT_ID, perfil: { capacidadPago: { amount: '3100.00', currency: 'BOB' } } }),
    );
    consents.crearConsentimiento.mockReturnValue(of({ id: CONSENT_ID }));
    integrations.listarIntegraciones.mockReturnValue(
      of([
        { fuente: FuenteIntegracion.Buro, estado: IntegracionEstadoEnum.Conectado },
        { fuente: FuenteIntegracion.Wallet, estado: IntegracionEstadoEnum.RequierePro },
      ]),
    );
    assessments.crearEvaluacion.mockReturnValue(of(result()));
    TestBed.configureTestingModule({
      imports: [Evaluation],
      providers: [
        provideRouter([]),
        { provide: SolicitantesService, useValue: applicants },
        { provide: ConsentimientosService, useValue: consents },
        { provide: IntegracionesService, useValue: integrations },
        { provide: EvaluacionesService, useValue: assessments },
        { provide: AuthSessionService, useValue: { session } },
        { provide: TenantUsageService, useValue: usage },
      ],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(Evaluation);
    fixture.detectChanges();
    return fixture;
  }
  const text = (f: ComponentFixture<Evaluation>) => (f.nativeElement as HTMLElement).textContent ?? '';

  function fillApplicant(f: ComponentFixture<Evaluation>): void {
    const c = f.componentInstance;
    c.document = ' CI-DEMO-1001 ';
    c.activity = 'Comercio';
    c.seniority = 36;
    c.income = 5000;
    c.amount = 3000;
    c.term = 12;
    c.purpose = 'Capital de trabajo';
  }

  async function goToResult(f: ComponentFixture<Evaluation>): Promise<void> {
    fillApplicant(f);
    await f.componentInstance.next();
    f.componentInstance.consentBureau = true;
    await f.componentInstance.next();
    await f.componentInstance.next();
    f.detectChanges();
  }

  it('no avanza sin los datos obligatorios del solicitante', async () => {
    const f = render();
    await f.componentInstance.next();
    f.detectChanges();
    expect(f.componentInstance.step()).toBe(0);
    expect(text(f)).toContain('Faltan datos del solicitante');
    expect(applicants.crearSolicitante).not.toHaveBeenCalled();
  });

  it('crea el solicitante si el documento no existe y lo busca por cabecera, no por URL', async () => {
    const f = render();
    fillApplicant(f);
    await f.componentInstance.next();

    expect(applicants.listarSolicitantes).toHaveBeenCalledWith(1, 1, undefined, 'CI-DEMO-1001');
    expect(applicants.crearSolicitante).toHaveBeenCalledWith(
      expect.objectContaining({
        documento: 'CI-DEMO-1001',
        actividad: 'Comercio',
        antiguedadActividadMeses: 36,
        ingresosMensuales: '5000.00',
        moneda: 'BOB',
        destinoCredito: 'Capital de trabajo',
      }),
    );
    expect(f.componentInstance.step()).toBe(1);
  });

  it('reutiliza el solicitante existente por documento', async () => {
    applicants.listarSolicitantes.mockReturnValue(of({ items: [{ id: APPLICANT_ID }], total: 1 }));
    const f = render();
    fillApplicant(f);
    await f.componentInstance.next();

    expect(applicants.crearSolicitante).not.toHaveBeenCalled();
    expect(f.componentInstance.applicantId()).toBe(APPLICANT_ID);
  });

  it('si el alta choca con otra simultánea (409), reutiliza la que ganó', async () => {
    applicants.listarSolicitantes
      .mockReturnValueOnce(of({ items: [], total: 0 }))
      .mockReturnValueOnce(of({ items: [{ id: APPLICANT_ID }], total: 1 }));
    applicants.crearSolicitante.mockReturnValue(throwError(() => problem(409, 'APPLICANT_ALREADY_EXISTS')));
    const f = render();
    fillApplicant(f);
    await f.componentInstance.next();

    expect(f.componentInstance.applicantId()).toBe(APPLICANT_ID);
    expect(f.componentInstance.step()).toBe(1);
  });

  it('exige el consentimiento del buró y registra solo las fuentes autorizadas', async () => {
    const f = render();
    fillApplicant(f);
    await f.componentInstance.next();
    await f.componentInstance.next();
    f.detectChanges();
    expect(text(f)).toContain('Falta el consentimiento obligatorio');
    expect(consents.crearConsentimiento).not.toHaveBeenCalled();

    f.componentInstance.consentBureau = true;
    f.componentInstance.consentWallet = true;
    await f.componentInstance.next();
    expect(consents.crearConsentimiento).toHaveBeenCalledWith(
      expect.objectContaining({ titularDocumento: 'CI-DEMO-1001', fuentes: ['BURO', 'WALLET'], vigenciaDias: 90 }),
    );
    expect(f.componentInstance.step()).toBe(2);
  });

  it('muestra las fuentes reales de GET /integraciones (y su carga)', async () => {
    integrations.listarIntegraciones.mockReturnValue(NEVER);
    const f = render();
    fillApplicant(f);
    await f.componentInstance.next();
    f.componentInstance.consentBureau = true;
    await f.componentInstance.next();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[aria-label="Cargando fuentes"]')).not.toBeNull();

    f.componentInstance.sources.set([{ fuente: FuenteIntegracion.Buro, estado: IntegracionEstadoEnum.Conectado }]);
    f.detectChanges();
    expect(text(f)).toContain('Buró de crédito');
    expect(text(f)).toContain('Conectado');
  });

  it('éxito: score, decisión, 3 razones, capacidad de pago y enlace al solicitante real', async () => {
    const f = render();
    await goToResult(f);

    expect(assessments.crearEvaluacion).toHaveBeenCalledWith(expect.any(String), {
      titularDocumento: 'CI-DEMO-1001',
      consentId: CONSENT_ID,
      proposito: 'Capital de trabajo',
      montoSolicitado: '3000.00',
      plazoMeses: 12,
    });
    expect(f.componentInstance.step()).toBe(3);
    expect(text(f)).toContain('820');
    expect(text(f)).toContain('Banda A');
    expect(text(f)).toContain('Preaprobado');
    expect(text(f)).toContain('Bs 3.100');
    expect(f.nativeElement.querySelectorAll('.ds-mono').length).toBe(3);
    expect(text(f)).not.toContain('PLAZO_HASTA_12_MESES');
    // La evaluación consumió cuota: se actualizan la barra y los avisos del 80 % y 100 % (E-11).
    expect(usage.refresh).toHaveBeenCalledOnce();
    expect(text(f)).toContain('Recomendación, no aprobación');
    // Sin promesas de funciones que la API todavía no expone (E-09).
    expect(text(f)).not.toMatch(/aparecerán aquí|próximamente/i);
    const link = [...f.nativeElement.querySelectorAll('a')].find((a: HTMLAnchorElement) =>
      a.textContent?.includes('Ver perfil completo'),
    ) as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe(`/app/clientes/${APPLICANT_ID}`);
  });

  it('PARCIAL: avisa que faltó una fuente y que no se preaprueba', async () => {
    assessments.crearEvaluacion.mockReturnValue(
      of(result({ estado: EvaluacionEstadoEnum.Parcial, decision: EvaluacionDecisionEnum.RevisionManual, score: 340, banda: EvaluacionBandaEnum.D })),
    );
    const f = render();
    await goToResult(f);

    expect(text(f)).toContain('Resultado parcial');
    expect(text(f)).toContain('Revisión manual');
    expect(text(f)).not.toContain('Preaprobado');
  });

  it('QUOTA_EXCEEDED: avisa del límite del plan y no avanza', async () => {
    assessments.crearEvaluacion.mockReturnValue(throwError(() => problem(429, 'QUOTA_EXCEEDED')));
    const f = render();
    await goToResult(f);

    expect(f.componentInstance.step()).toBe(2);
    expect(text(f)).toContain('Llegaste al límite de evaluaciones de tu plan');
    expect(usage.refresh).toHaveBeenCalledOnce();
    // Un analista no puede abrir Configuración: no se le ofrece un enlace que el guard rechazaría.
    expect(f.nativeElement.querySelector('a[href="/app/configuracion#plan"]')).toBeNull();
    expect(text(f)).toContain('Pide a quien administra tu institución que revise el plan.');
  });

  it('QUOTA_EXCEEDED: a TENANT_ADMIN le enlaza su plan en Configuración', async () => {
    session.set({ isAuthenticated: true, name: 'Admin', tenantId: 't', roles: ['TENANT_ADMIN'] });
    assessments.crearEvaluacion.mockReturnValue(throwError(() => problem(429, 'QUOTA_EXCEEDED')));
    const f = render();
    await goToResult(f);

    const link = f.nativeElement.querySelector('a[href="/app/configuracion#plan"]') as HTMLAnchorElement;
    expect(link.textContent?.trim()).toBe('Ver tu plan');
    expect(text(f)).not.toContain('Pide a quien administra');
  });

  it('al cambiar de paso, el foco pasa al título del paso nuevo (el botón pulsado puede desaparecer)', async () => {
    const f = render();
    const heading = () => f.nativeElement.querySelector('h2') as HTMLHeadingElement;
    // Al abrir la vista el foco no se mueve: lo conserva el enlace o el salto de contenido.
    expect(document.activeElement).not.toBe(heading());

    await goToResult(f);
    await f.whenStable();
    expect(heading().textContent?.trim()).toBe('Resultado');
    expect(document.activeElement).toBe(heading());

    f.componentInstance.restart();
    f.detectChanges();
    await f.whenStable();
    expect(heading().textContent?.trim()).toBe('Solicitante');
    expect(document.activeElement).toBe(heading());
  });

  it('CONSENT_REQUIRED: vuelve al consentimiento para registrar uno nuevo', async () => {
    assessments.crearEvaluacion.mockReturnValue(throwError(() => problem(422, 'CONSENT_REQUIRED')));
    const f = render();
    await goToResult(f);

    expect(f.componentInstance.step()).toBe(1);
    expect(f.componentInstance.consentId()).toBeNull();
    expect(text(f)).toContain('El consentimiento no es válido');
  });

  it('un error al evaluar permite reintentar con la misma Idempotency-Key (sin duplicar)', async () => {
    assessments.crearEvaluacion.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    const f = render();
    await goToResult(f);
    expect(text(f)).toContain('No pudimos completar la evaluación');

    await f.componentInstance.next();
    const [first, second] = assessments.crearEvaluacion.mock.calls.map((call) => call[0]);
    expect(second).toBe(first);
    expect(f.componentInstance.step()).toBe(3);
  });

  it('«Nueva evaluación» empieza de cero con otra Idempotency-Key', async () => {
    const f = render();
    await goToResult(f);
    const firstKey = assessments.crearEvaluacion.mock.calls[0][0];

    f.componentInstance.restart();
    await goToResult(f);
    expect(assessments.crearEvaluacion.mock.calls[1][0]).not.toBe(firstKey);
  });
});
