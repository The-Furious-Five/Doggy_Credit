import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import {
  AuditoriaService,
  TenantActual,
  TenantActualPlanEnum,
  MiembroEquipoEstadoEnum,
  TenantActualTipoEnum,
  TenantService,
} from '../api';
import { AuthSessionService } from '../auth/session.service';
import { Settings } from './settings';

const tenant = (plan: TenantActualPlanEnum): TenantActual => ({
  id: 'tenant-prueba',
  nombre: 'Institución de prueba',
  nit: 'nit-prueba',
  tipo: TenantActualTipoEnum.Imf,
  plan,
  pais: 'BO',
  moneda: 'BOB',
  activo: true,
});
const failure = () => throwError(() => new HttpErrorResponse({ status: 500 }));

describe('Configuración con datos de la API (E-09)', () => {
  const api = {
    obtenerTenantActual: vi.fn(),
    obtenerUsoTenant: vi.fn(),
    listarEquipo: vi.fn(),
    obtenerPreferenciasTenant: vi.fn(),
  };

  const roles = signal(['TENANT_ADMIN']);

  beforeEach(() => {
    vi.resetAllMocks();
    roles.set(['TENANT_ADMIN']);
    api.obtenerTenantActual.mockReturnValue(of(tenant(TenantActualPlanEnum.Free)));
    api.obtenerUsoTenant.mockReturnValue(of({ evaluacionesMes: 120, limiteMensual: 500 }));
    api.listarEquipo.mockReturnValue(of([]));
    api.obtenerPreferenciasTenant.mockReturnValue(of({ enmascararDatos: true }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: TenantService, useValue: api },
        // Configuración › Seguridad incluye la bitácora (C-08).
        { provide: AuditoriaService, useValue: { listarAuditoria: () => of({ items: [] }) } },
        {
          provide: AuthSessionService,
          useValue: {
            session: () => ({
              isAuthenticated: true,
              name: 'Admin',
              tenantId: 'tenant-prueba',
              roles: roles(),
            }),
          },
        },
      ],
    });
  });

  async function render(): Promise<ComponentFixture<Settings>> {
    const fixture = TestBed.createComponent(Settings);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  const page = (f: ComponentFixture<Settings>) => f.nativeElement as HTMLElement;
  const plan = (f: ComponentFixture<Settings>) =>
    page(f).querySelector('ds-tab[value="plan"]') as HTMLElement;

  it('muestra el plan y la cuota que devuelve la API, sin catálogo de precios local', async () => {
    const f = await render();
    expect(plan(f).querySelector('h2')?.textContent?.trim()).toBe('Plan Gratis');
    expect(plan(f).textContent).toContain('Plan actual');
    expect(plan(f).textContent).toContain('120 de 500 evaluaciones utilizadas este mes');
    expect(plan(f).querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('24');
    // Precios y características son textos de la landing, no datos de la consola.
    expect(plan(f).textContent).not.toMatch(/Bs 2\.900|500 consultas al mes|Hasta 3 usuarios/);
  });

  it('no simula la contratación: sin botón de solo aviso y con enlace a la comparación de planes', async () => {
    const f = await render();
    expect(plan(f).querySelector('button')).toBeNull();
    expect(plan(f).textContent).not.toContain('Te contactaremos');
    expect(plan(f).textContent).toContain('El equipo de DoggyCredit activa los cambios de plan');
    const compare = plan(f).querySelector('a[href="/#planes"]');
    expect(compare?.textContent?.trim()).toBe('Comparar planes');
  });

  it('presenta un plan sin límite mensual sin barra de progreso', async () => {
    api.obtenerTenantActual.mockReturnValue(of(tenant(TenantActualPlanEnum.Pro)));
    api.obtenerUsoTenant.mockReturnValue(of({ evaluacionesMes: 42, limiteMensual: 0 }));
    const f = await render();
    expect(plan(f).querySelector('h2')?.textContent?.trim()).toBe('Plan Pro');
    expect(plan(f).textContent).toContain(
      '42 evaluaciones utilizadas este mes. Tu plan no tiene límite mensual.',
    );
    expect(plan(f).querySelector('[role="progressbar"]')).toBeNull();
  });

  it('distingue la carga del plan de un plan gratuito', async () => {
    api.obtenerTenantActual.mockReturnValue(new Subject());
    const f = await render();
    expect(plan(f).textContent).toContain('Cargando plan…');
    expect(plan(f).textContent).not.toContain('Plan Gratis');
    expect(page(f).textContent).toContain('Cargando…');
  });

  it('si falla el uso mensual conserva el plan y lo avisa', async () => {
    api.obtenerUsoTenant.mockReturnValue(failure());
    const f = await render();
    expect(plan(f).querySelector('h2')?.textContent?.trim()).toBe('Plan Gratis');
    expect(plan(f).textContent).toContain('No se pudo consultar el uso');
  });

  it('sigue utilizable si fallan el tenant o las preferencias', async () => {
    api.obtenerTenantActual.mockReturnValue(failure());
    api.obtenerPreferenciasTenant.mockReturnValue(failure());
    const f = await render();
    expect(plan(f).textContent).toContain('No se pudo consultar el plan');
    expect(page(f).querySelector('ds-tab[value="organizacion"]')?.textContent).toContain(
      'No disponible',
    );
    expect(page(f).textContent).toContain('No se pudo consultar la preferencia');
    // Sin la preferencia guardada se muestra el valor seguro: ocultar.
    expect(page(f).querySelector('[role="switch"]')?.getAttribute('aria-checked')).toBe('true');
  });

  const team = (f: ComponentFixture<Settings>) =>
    page(f).querySelector('ds-tab[value="equipo"]') as HTMLElement;

  it('Equipo: distingue la carga, el directorio vacío y el error con reintento (A-10)', async () => {
    const pending = new Subject<never>();
    api.listarEquipo.mockReturnValue(pending);
    let f = await render();
    expect(team(f).textContent).toContain('Cargando usuarios…');

    api.listarEquipo.mockReturnValue(of([]));
    f = await render();
    expect(team(f).querySelector('ds-empty-state')?.textContent).toContain('Todavía no hay usuarios');
    expect(team(f).querySelector('table')).toBeNull();

    api.listarEquipo.mockReturnValue(failure());
    f = await render();
    expect(team(f).textContent).toContain('Equipo no disponible');
    api.listarEquipo.mockReturnValue(
      of([{ nombre: 'Rosa Mamani', email: 'rosa@example.test', rol: 'ANALYST', estado: MiembroEquipoEstadoEnum.Invitado }]),
    );
    [...team(f).querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Reintentar')!.click();
    f.detectChanges();
    expect(team(f).querySelector('tbody')?.textContent).toContain('Rosa Mamani');
    expect(team(f).querySelector('tbody')?.textContent).toContain('Invitación enviada');
  });

  it('Equipo: solo el administrador de la institución ve «Invitar»', async () => {
    let f = await render();
    expect(team(f).querySelector('dc-team-invite')).not.toBeNull();

    roles.set(['ANALYST']);
    f = await render();
    expect(team(f).querySelector('dc-team-invite')).toBeNull();
  });
});
