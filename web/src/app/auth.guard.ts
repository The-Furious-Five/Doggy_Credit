import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { catchError, map, of } from 'rxjs';

/** La consola solo se muestra con sesión; sin ella se vuelve al acceso. La API valida igualmente. */
export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(OidcSecurityService)
    .checkAuth()
    .pipe(
      map(({ isAuthenticated }) => isAuthenticated || router.parseUrl('/acceso')),
      catchError(() => of(router.parseUrl('/acceso'))),
    );
};
