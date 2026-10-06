import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { DsToastService } from '../design-system';
import { isAppRole } from './permissions';
import { AuthSessionService } from './session.service';

export const roleGuard: CanActivateFn = (route) => {
  const router = inject(Router);
  const toast = inject(DsToastService);
  const requiredRole: unknown = route.data['requiredRole'];
  return inject(AuthSessionService)
    .check()
    .pipe(
      map(({ isAuthenticated, roles }) => {
        if (!isAuthenticated) return router.parseUrl('/acceso');
        if (isAppRole(requiredRole) && roles.includes(requiredRole)) return true;
        toast.show('Tu cuenta no tiene permiso para abrir esta vista.');
        return router.parseUrl('/app/resumen');
      }),
      catchError(() => of(router.parseUrl('/acceso'))),
    );
};
