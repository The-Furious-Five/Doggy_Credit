import { DOCUMENT, inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { switchMap, take } from 'rxjs';
import { BASE_PATH } from '../api';
import { isApiRequest } from './api-request';

export const jwtInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isApiRequest(request.url, inject(BASE_PATH), inject(DOCUMENT).baseURI)) {
    return next(request);
  }
  // Leer el token vigente en cada petición permite utilizar la renovación de OIDC.
  return inject(OidcSecurityService)
    .getAccessToken()
    .pipe(
      take(1),
      switchMap((token) =>
        next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request),
      ),
    );
};
