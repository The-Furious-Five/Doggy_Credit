import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { TenantActual, TenantActualPlanEnum, TenantActualTipoEnum, TenantService } from '../api';
import { AppRole } from '../auth/permissions';
import { DsToastService } from '../design-system';
import { Console } from './console';

const tenant: TenantActual = {
  id: 'tenant-prueba',
  nombre: 'Institución de prueba',
  nit: 'nit-prueba',
  tipo: TenantActualTipoEnum.Imf,
  plan: TenantActualPlanEnum.Free,
  pais: 'BO',
  moneda: 'BOB',
  activo: true,
};

describe('Consola y barra A-01', () => {
  let roles: AppRole[];
  let authenticated: BehaviorSubject<{ isAuthenticated: boolean }>;
  const oidc = { getPayloadFromAccessToken: vi.fn(), logoff: vi.fn() };
  const api = { obtenerTenantActual: vi.fn(), obtenerUsoTenant: vi.fn() };

  beforeEach(() => {
    vi.resetAllMocks();
    roles = ['ANALYST'];
    authenticated = new BehaviorSubject<{ isAuthenticated: boolean }>({ isAuthenticated: true });
    oidc.getPayloadFromAccessToken.mockImplementation(() =>
      of({
        tenant_id: tenant.id,
        realm_access: { roles },
      }),
    );
    oidc.logoff.mockReturnValue(of(null));
    api.obtenerTenantActual.mockReturnValue(of(tenant));
    api.obtenerUsoTenant.mockReturnValue(of({ evaluacionesMes: 12, limiteMensual: 50 }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: TenantService, useValue: api },
        {
          provide: OidcSecurityService,
          useValue: {
            ...oidc,
            isAuthenticated$: authenticated,
            userData$: of({
              userData: { name: 'Persona de prueba', preferred_username: 'usuario.prueba' },
            }),
          },
        },
      ],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(Console);
    fixture.detectChanges();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('muestra el usuario de OIDC y el nombre del tenant obtenido de la API', () => {
    const { page } = render();
    const top = page.querySelector('.console-top')!;
    expect(top.textContent).toContain('Persona de prueba');
    expect(top.textContent).toContain('Institución de prueba');
    expect(top.textContent).not.toContain(tenant.id);
    expect(page.querySelector('[aria-label="Cuenta de Persona de prueba"]')).not.toBeNull();
    expect(page.textContent).toContain('12 de 50 este mes');
    // «Datos simulados» solo acompaña respuestas marcadas por la API (simulado=true), nunca la barra fija (E-09).
    expect(top.textContent).not.toMatch(/simulad/i);
  });

  it.each(['ANALYST', 'API_CLIENT', 'PLATFORM_ADMIN'] as AppRole[])(
    'oculta a %s todos los enlaces reservados a TENANT_ADMIN',
    (role) => {
      roles = [role];
      const { fixture, page } = render();
      for (const path of [
        '/app/politicas',
        '/app/productos',
        '/app/configuracion',
        '/app/desarrolladores',
      ]) {
        expect(page.querySelector(`a[href="${path}"]`)).toBeNull();
      }
      expect(page.querySelector('a[href="/app/configuracion#plan"]')).toBeNull();
      expect(fixture.componentInstance.account().some((item) => item.id === 'profile')).toBe(false);
      expect(page.querySelector('a[href="/app/clientes"]')).not.toBeNull();
    },
  );

  it('muestra a TENANT_ADMIN sus vistas y el acceso a mejorar el plan', () => {
    roles = ['TENANT_ADMIN'];
    const { fixture, page } = render();
    for (const path of [
      '/app/politicas',
      '/app/productos',
      '/app/configuracion',
      '/app/desarrolladores',
    ]) {
      expect(page.querySelector(`a[href="${path}"]`)).not.toBeNull();
    }
    expect(page.querySelector('a[href="/app/configuracion#plan"]')).not.toBeNull();
    expect(fixture.componentInstance.account().some((item) => item.id === 'profile')).toBe(true);
    expect(page.querySelector('a[href="/app/admin/tenants"]')).toBeNull();
  });

  it('reserva el enlace de administración de tenants a PLATFORM_ADMIN', () => {
    roles = ['PLATFORM_ADMIN'];
    const { page } = render();
    expect(page.querySelector('a[href="/app/admin/tenants"]')).not.toBeNull();
  });

  it('actualiza el menú cuando se renuevan los roles y lo vacía sin sesión', () => {
    const { fixture, page } = render();
    expect(page.querySelector('a[href="/app/politicas"]')).toBeNull();
    roles = ['TENANT_ADMIN'];
    authenticated.next({ isAuthenticated: true });
    fixture.detectChanges();
    expect(page.querySelector('a[href="/app/politicas"]')).not.toBeNull();
    authenticated.next({ isAuthenticated: false });
    fixture.detectChanges();
    expect(page.querySelectorAll('.console-nav a')).toHaveLength(0);
    expect(page.textContent).not.toContain('Persona de prueba');
  });

  it('muestra el aviso de cuota (E-11) encima de la vista al llegar al 80 %', () => {
    api.obtenerUsoTenant.mockReturnValue(of({ evaluacionesMes: 45, limiteMensual: 50 }));
    const { page } = render();
    const notice = page.querySelector('main#contenido > dc-quota-notice');
    expect(notice?.textContent).toContain('Usaste el 90 % de las evaluaciones del mes');
    // La barra lateral y el aviso leen el mismo uso: una sola consulta.
    expect(api.obtenerUsoTenant).toHaveBeenCalledOnce();
  });

  it('distingue la carga de tenant de un plan gratuito', () => {
    api.obtenerTenantActual.mockReturnValue(new Subject());
    const { page } = render();
    expect(page.textContent).toContain('Cargando institución…');
    expect(page.textContent).toContain('Cargando plan…');
    expect(page.textContent).not.toContain('Plan Gratis');
  });

  it('mantiene la consola utilizable si falla la carga de tenant o uso', () => {
    api.obtenerTenantActual.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    api.obtenerUsoTenant.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const { page } = render();
    expect(page.textContent).toContain('Institución no disponible');
    expect(page.textContent).toContain('Plan no disponible');
    expect(page.querySelector('a[href="/app/clientes"]')).not.toBeNull();
  });

  it('cierra sesión en Keycloak al seleccionar la acción del menú', () => {
    const { fixture, page } = render();
    page.querySelector<HTMLButtonElement>('[aria-label="Cuenta de Persona de prueba"]')!.click();
    fixture.detectChanges();
    const logout = [...page.querySelectorAll<HTMLElement>('[role="menuitem"]')].find((item) =>
      item.textContent?.includes('Cerrar sesión'),
    )!;
    logout.click();
    expect(oidc.logoff).toHaveBeenCalledExactlyOnceWith();
  });

  it('bloquea cierres duplicados y permite reintentar si falla Keycloak', () => {
    const pending = new Subject();
    oidc.logoff.mockReturnValue(pending);
    const { fixture } = render();
    fixture.componentInstance.onAccount('logout');
    fixture.componentInstance.onAccount('logout');
    expect(oidc.logoff).toHaveBeenCalledOnce();
    expect(fixture.componentInstance.account().every((item) => item.disabled)).toBe(true);
    pending.error(new Error('Keycloak unavailable'));
    expect(fixture.componentInstance.loggingOut()).toBe(false);
    expect(TestBed.inject(DsToastService).message()).toContain('No se pudo cerrar la sesión');
    oidc.logoff.mockReturnValue(of(null));
    fixture.componentInstance.onAccount('logout');
    expect(oidc.logoff).toHaveBeenCalledTimes(2);
  });

  it('no dirige al analista a configuración desde una acción de perfil no disponible', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    render().fixture.componentInstance.onAccount('profile');
    expect(navigate).not.toHaveBeenCalled();
  });
});
