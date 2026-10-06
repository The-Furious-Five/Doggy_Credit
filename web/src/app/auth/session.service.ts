import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { Observable, catchError, combineLatest, map, of, switchMap, take } from 'rxjs';
import { AppRole, isAppRole } from './permissions';

export interface AuthSession {
  readonly isAuthenticated: boolean;
  readonly name: string;
  readonly tenantId: string;
  readonly roles: readonly AppRole[];
}

const anonymous: AuthSession = { isAuthenticated: false, name: '', tenantId: '', roles: [] };

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function authenticatedSession(userData: unknown, accessTokenClaims: unknown): AuthSession {
  const user = record(userData);
  const claims = record(accessTokenClaims);
  const roles = record(claims['realm_access'])['roles'];
  return {
    isAuthenticated: true,
    name:
      text(user['name']) ||
      text(user['preferred_username']) ||
      text(claims['preferred_username']) ||
      'Usuario',
    tenantId: text(claims['tenant_id']),
    // El access token contiene los permisos que recibe la API. UserInfo no los sustituye.
    roles: Array.isArray(roles) ? roles.filter(isAppRole) : [],
  };
}

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly oidc = inject(OidcSecurityService);
  readonly session = toSignal(
    combineLatest([this.oidc.isAuthenticated$, this.oidc.userData$]).pipe(
      switchMap(([{ isAuthenticated }, { userData }]) =>
        this.readSession(isAuthenticated, userData).pipe(catchError(() => of(anonymous))),
      ),
    ),
    { initialValue: anonymous },
  );

  /** Comprueba también al cambiar de hija: entrar a /app no autoriza toda la sesión. */
  check(): Observable<AuthSession> {
    return this.oidc.checkAuth().pipe(
      switchMap(({ isAuthenticated, userData }) => this.readSession(isAuthenticated, userData)),
      take(1),
    );
  }

  logout(): Observable<unknown> {
    // Usa el end_session_endpoint descubierto de Keycloak y postLogoutRedirectUri.
    return this.oidc.logoff();
  }

  private readSession(isAuthenticated: boolean, userData: unknown): Observable<AuthSession> {
    return isAuthenticated
      ? this.oidc
          .getPayloadFromAccessToken()
          .pipe(map((claims: unknown) => authenticatedSession(userData, claims)))
      : of(anonymous);
  }
}
