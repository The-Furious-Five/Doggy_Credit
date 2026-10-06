import { TestBed } from '@angular/core/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { BehaviorSubject, firstValueFrom, of, throwError } from 'rxjs';
import { AuthSessionService } from './session.service';

describe('Sesión OIDC', () => {
  let authenticated: BehaviorSubject<{ isAuthenticated: boolean }>;
  let userData: BehaviorSubject<{ userData: unknown }>;
  let claims: unknown;
  let oidc: {
    isAuthenticated$: typeof authenticated;
    userData$: typeof userData;
    checkAuth: ReturnType<typeof vi.fn>;
    getPayloadFromAccessToken: ReturnType<typeof vi.fn>;
    logoff: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authenticated = new BehaviorSubject<{ isAuthenticated: boolean }>({ isAuthenticated: false });
    userData = new BehaviorSubject<{ userData: unknown }>({ userData: null });
    claims = { tenant_id: 'tenant-prueba', realm_access: { roles: ['ANALYST'] } };
    oidc = {
      isAuthenticated$: authenticated,
      userData$: userData,
      checkAuth: vi.fn(() =>
        of({
          isAuthenticated: authenticated.value.isAuthenticated,
          userData: userData.value.userData,
        }),
      ),
      getPayloadFromAccessToken: vi.fn(() => of(claims)),
      logoff: vi.fn(() => of(null)),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: OidcSecurityService, useValue: oidc }],
    });
  });

  it('toma identidad de UserInfo y permisos y tenant del access token', async () => {
    userData.next({
      userData: {
        name: '  Persona de prueba  ',
        tenant_id: 'tenant-distinto',
        realm_access: { roles: ['TENANT_ADMIN'] },
      },
    });
    authenticated.next({ isAuthenticated: true });
    const auth = TestBed.inject(AuthSessionService);
    const expected = {
      isAuthenticated: true,
      name: 'Persona de prueba',
      tenantId: 'tenant-prueba',
      roles: ['ANALYST'],
    };
    expect(auth.session()).toEqual(expected);
    expect(await firstValueFrom(auth.check())).toEqual(expected);
  });

  it('no concede permisos de datos de usuario residuales sin sesión', async () => {
    userData.next({
      userData: { name: 'Sesión anterior', realm_access: { roles: ['TENANT_ADMIN'] } },
    });
    const auth = TestBed.inject(AuthSessionService);
    expect(await firstValueFrom(auth.check())).toEqual({
      isAuthenticated: false,
      name: '',
      tenantId: '',
      roles: [],
    });
    expect(oidc.getPayloadFromAccessToken).not.toHaveBeenCalled();
  });

  it('actualiza los permisos al renovar el token y los elimina al expirar', () => {
    authenticated.next({ isAuthenticated: true });
    const auth = TestBed.inject(AuthSessionService);
    expect(auth.session().roles).toEqual(['ANALYST']);
    claims = { realm_access: { roles: ['TENANT_ADMIN'] } };
    authenticated.next({ isAuthenticated: true });
    expect(auth.session().roles).toEqual(['TENANT_ADMIN']);
    authenticated.next({ isAuthenticated: false });
    expect(auth.session().roles).toEqual([]);
    expect(auth.session().tenantId).toBe('');
  });

  it.each([
    null,
    {},
    { realm_access: { roles: 'TENANT_ADMIN' } },
    { realm_access: { roles: [null, 5, 'SUPER_ADMIN'] } },
  ])('no concede permisos a claims ausentes o mal formados (%s)', async (payload) => {
    claims = payload;
    authenticated.next({ isAuthenticated: true });
    expect((await firstValueFrom(TestBed.inject(AuthSessionService).check())).roles).toEqual([]);
  });

  it('usa el username cuando no existe un nombre completo', () => {
    userData.next({ userData: { preferred_username: 'usuario.prueba' } });
    authenticated.next({ isAuthenticated: true });
    expect(TestBed.inject(AuthSessionService).session().name).toBe('usuario.prueba');
  });

  it('retira permisos si falla la lectura de claims y permite recuperarse', () => {
    authenticated.next({ isAuthenticated: true });
    oidc.getPayloadFromAccessToken.mockReturnValue(throwError(() => new Error('invalid claims')));
    const auth = TestBed.inject(AuthSessionService);
    expect(auth.session().roles).toEqual([]);
    oidc.getPayloadFromAccessToken.mockImplementation(() => of(claims));
    authenticated.next({ isAuthenticated: true });
    expect(auth.session().roles).toEqual(['ANALYST']);
  });

  it('cierra la sesión mediante el logout de Keycloak de la biblioteca OIDC', async () => {
    await firstValueFrom(TestBed.inject(AuthSessionService).logout());
    expect(oidc.logoff).toHaveBeenCalledExactlyOnceWith();
  });
});
