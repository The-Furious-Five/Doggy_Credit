import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthSessionService } from './session.service';

/** La consola solo se muestra con sesión; sin ella se vuelve al acceso. La API valida igualmente. */
export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthSessionService)
    .check()
    .pipe(
      map(({ isAuthenticated }) => isAuthenticated || router.parseUrl('/acceso')),
      catchError(() => of(router.parseUrl('/acceso'))),
    );
};
