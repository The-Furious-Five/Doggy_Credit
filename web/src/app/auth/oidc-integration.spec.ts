import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';
import { appConfig } from '../app.config';
import { AuthSessionService } from './session.service';

describe('Logout con la biblioteca OIDC y configuración reales', () => {
  let requests: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    requests = TestBed.inject(HttpTestingController);
  });

  afterEach(() => requests.verify());

  it('descubre y utiliza el endpoint de logout de Keycloak con el retorno configurado', async () => {
    const oidc = TestBed.inject(OidcSecurityService);
    const config = await firstValueFrom(oidc.getConfiguration());
    const authority = `${window.location.origin}/auth/realms/doggycredit`;
    expect(config!.authority).toBe(authority);
    expect(config!.clientId).toBe('doggycredit-web');
    expect(config!.postLogoutRedirectUri).toBe(`${window.location.origin}/`);

    const discovered = firstValueFrom(oidc.preloadAuthWellKnownDocument());
    const discovery = requests.expectOne(`${authority}/.well-known/openid-configuration`);
    expect(discovery.request.headers.has('Authorization')).toBe(false);
    const endpoint = `${authority}/protocol/openid-connect/logout`;
    discovery.flush({
      issuer: authority,
      authorization_endpoint: `${authority}/protocol/openid-connect/auth`,
      token_endpoint: `${authority}/protocol/openid-connect/token`,
      userinfo_endpoint: `${authority}/protocol/openid-connect/userinfo`,
      jwks_uri: `${authority}/protocol/openid-connect/certs`,
      end_session_endpoint: endpoint,
    });
    await discovered;

    // Ejecutar logoff real y capturar únicamente la navegación externa: jsdom
    // no implementa redirecciones entre sitios. No se sustituye la lógica OIDC.
    const redirect = vi.fn();
    const logoff = oidc.logoff.bind(oidc);
    vi.spyOn(oidc, 'logoff').mockImplementation(() => logoff(undefined, { urlHandler: redirect }));
    await firstValueFrom(TestBed.inject(AuthSessionService).logout());
    expect(redirect).toHaveBeenCalledOnce();
    const logoutUrl = new URL(redirect.mock.calls[0][0]);
    expect(`${logoutUrl.origin}${logoutUrl.pathname}`).toBe(endpoint);
    expect(logoutUrl.searchParams.get('post_logout_redirect_uri')).toBe(
      `${window.location.origin}/`,
    );
    expect((await firstValueFrom(oidc.isAuthenticated$)).isAuthenticated).toBe(false);
  });
});
