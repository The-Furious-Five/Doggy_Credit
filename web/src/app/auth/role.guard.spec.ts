import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';
import { DsToastService } from '../design-system';
import { roleGuard } from './role.guard';
import { AuthSessionService } from './session.service';

describe('Guard de rol', () => {
  const auth = { check: vi.fn() };
  const toast = { show: vi.fn() };

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: auth },
        { provide: DsToastService, useValue: toast },
      ],
    });
  });

  async function run(requiredRole: unknown) {
    const route = new ActivatedRouteSnapshot();
    route.data = { requiredRole };
    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() => roleGuard(route, {} as never)) as Observable<
        boolean | UrlTree
      >,
    );
    return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : result;
  }

  it.each([undefined, 'SUPER_ADMIN', ['TENANT_ADMIN']])(
    'deniega una ruta cuya política de rol no es válida (%s)',
    async (requiredRole) => {
      auth.check.mockReturnValue(of({ isAuthenticated: true, roles: ['TENANT_ADMIN'] }));
      expect(await run(requiredRole)).toBe('/app/resumen');
      expect(toast.show).toHaveBeenCalledOnce();
    },
  );

  it('vuelve al acceso sin sesión y no muestra un error de permisos', async () => {
    auth.check.mockReturnValue(of({ isAuthenticated: false, roles: ['TENANT_ADMIN'] }));
    expect(await run('TENANT_ADMIN')).toBe('/acceso');
    expect(toast.show).not.toHaveBeenCalled();
  });

  it('deniega el acceso si no se puede comprobar la sesión', async () => {
    auth.check.mockReturnValue(throwError(() => new Error('offline')));
    expect(await run('TENANT_ADMIN')).toBe('/acceso');
  });
});
