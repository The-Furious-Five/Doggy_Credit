import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, Routes, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { routes } from './app.routes';
import { AppRole } from './auth/permissions';
import { DsToastService } from './design-system';

@Component({ imports: [RouterOutlet], template: '<router-outlet />' })
class TestLayout {}

@Component({ template: '<p>Vista de prueba</p>' })
class TestPage {}

const consoleRoute = routes.find((route) => route.path === 'app')!;
const consolePaths = consoleRoute
  .children!.filter((route) => route.path)
  .map((route) => `/app/${route.path!.replace(':id', 'cliente-prueba')}`);
const tenantAdminPaths = [
  '/app/politicas',
  '/app/productos',
  '/app/configuracion',
  '/app/desarrolladores',
];

describe('Protección de las rutas reales /app/*', () => {
  let loggedIn: boolean;
  let roles: AppRole[];
  let authentication: BehaviorSubject<{ isAuthenticated: boolean }>;
  let oidc: { checkAuth: ReturnType<typeof vi.fn> };
  let loads: Map<string, ReturnType<typeof vi.fn>>;
  const toast = { show: vi.fn() };

  beforeEach(() => {
    loggedIn = true;
    roles = ['ANALYST'];
    authentication = new BehaviorSubject<{ isAuthenticated: boolean }>({
      isAuthenticated: loggedIn,
    });
    oidc = {
      checkAuth: vi.fn(() =>
        of({
          isAuthenticated: loggedIn,
          userData: { name: 'Persona de prueba', realm_access: { roles: ['TENANT_ADMIN'] } },
        }),
      ),
    };
    loads = new Map();
    toast.show.mockReset();
    // Sustituir únicamente las vistas aísla las peticiones de negocio. Se conservan
    // las rutas, redirecciones, guards y políticas de la configuración de producción.
    const testRoutes: Routes = routes.map((route) => {
      if (route.path !== 'app')
        return route.redirectTo !== undefined
          ? route
          : {
              ...route,
              component: TestPage,
              loadComponent: undefined,
            };
      return {
        ...route,
        loadComponent: () => Promise.resolve(TestLayout),
        children: route.children!.map((child) => {
          if (!child.loadComponent) return child;
          const load = vi.fn(() => Promise.resolve(TestPage));
          loads.set(`/app/${child.path}`, load);
          return { ...child, loadComponent: load };
        }),
      };
    });
    TestBed.configureTestingModule({
      providers: [
        provideRouter(testRoutes),
        {
          provide: OidcSecurityService,
          useValue: {
            ...oidc,
            isAuthenticated$: authentication,
            userData$: of({ userData: { name: 'Persona de prueba' } }),
            getPayloadFromAccessToken: vi.fn(() => of({ realm_access: { roles } })),
          },
        },
        { provide: DsToastService, useValue: toast },
      ],
    });
  });

  async function navigate(path: string): Promise<string> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(path);
    return TestBed.inject(Router).url;
  }

  it.each(['/app', ...consolePaths])('sin sesión bloquea el acceso directo a %s', async (path) => {
    loggedIn = false;
    authentication.next({ isAuthenticated: false });
    expect(await navigate(path)).toBe('/acceso');
    expect([...loads.values()].every((load) => load.mock.calls.length === 0)).toBe(true);
  });

  it.each(tenantAdminPaths)(
    'ANALYST no puede abrir %s aunque UserInfo anuncie un rol admin',
    async (path) => {
      expect(await navigate(path)).toBe('/app/resumen');
      expect(loads.get(path)).not.toHaveBeenCalled();
      expect(toast.show).toHaveBeenCalledOnce();
    },
  );

  it.each(tenantAdminPaths)('TENANT_ADMIN puede abrir %s', async (path) => {
    roles = ['TENANT_ADMIN'];
    expect(await navigate(path)).toBe(path);
    expect(loads.get(path)).toHaveBeenCalledOnce();
  });

  it.each(tenantAdminPaths)('PLATFORM_ADMIN sin TENANT_ADMIN no puede abrir %s', async (path) => {
    roles = ['PLATFORM_ADMIN'];
    expect(await navigate(path)).toBe('/app/resumen');
  });

  it.each(['ANALYST', 'TENANT_ADMIN', 'API_CLIENT'] as AppRole[])(
    '%s no puede abrir la administración de tenants',
    async (role) => {
      roles = [role];
      expect(await navigate('/app/admin/tenants')).toBe('/app/resumen');
      expect(loads.get('/app/admin/tenants')).not.toHaveBeenCalled();
    },
  );

  it('PLATFORM_ADMIN puede abrir la administración de tenants', async () => {
    roles = ['PLATFORM_ADMIN'];
    expect(await navigate('/app/admin/tenants')).toBe('/app/admin/tenants');
  });

  it.each([
    '/app/resumen',
    '/app/clientes',
    '/app/clientes/cliente-prueba',
    '/app/evaluaciones/nueva',
    '/app/analitica',
    '/app/integraciones',
  ])('ANALYST puede abrir la vista operativa %s', async (path) =>
    expect(await navigate(path)).toBe(path),
  );

  it('vuelve al acceso si la sesión expira entre dos vistas de la misma consola', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/app/resumen');
    loggedIn = false;
    authentication.next({ isAuthenticated: false });
    await harness.navigateByUrl('/app/clientes');
    expect(TestBed.inject(Router).url).toBe('/acceso');
    expect(loads.get('/app/clientes')).not.toHaveBeenCalled();
  });

  it('revalida el rol al cambiar entre vistas administrativas', async () => {
    roles = ['TENANT_ADMIN'];
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/app/productos');
    roles = ['ANALYST'];
    await harness.navigateByUrl('/app/configuracion');
    expect(TestBed.inject(Router).url).toBe('/app/resumen');
    expect(loads.get('/app/configuracion')).not.toHaveBeenCalled();
  });

  it('bloquea la consola si falla la comprobación OIDC', async () => {
    oidc.checkAuth.mockReturnValue(throwError(() => new Error('Keycloak unavailable')));
    expect(await navigate('/app/resumen')).toBe('/acceso');
  });

  it.each(['/', '/acceso', '/callback', '/design-system'])(
    'mantiene pública la ruta %s sin consultar OIDC desde los guards',
    async (path) => {
      loggedIn = false;
      expect(await navigate(path)).toBe(path);
      expect(oidc.checkAuth).not.toHaveBeenCalled();
    },
  );
});
