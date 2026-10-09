import { DOCUMENT, inject } from '@angular/core';
import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { BASE_PATH } from '../api';
import { DsToastService } from '../design-system';
import { isApiRequest } from './api-request';

function nonEmptyText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function problemBody(body: unknown): Record<string, unknown> {
  if (typeof body === 'string') {
    try {
      body = JSON.parse(body);
    } catch {
      return {};
    }
  }
  return body !== null && typeof body === 'object' && !Array.isArray(body)
    ? (body as Record<string, unknown>)
    : {};
}

function errorMessage(error: HttpErrorResponse): string {
  const problem = problemBody(error.error);
  const title = nonEmptyText(problem['title']);
  const detail = nonEmptyText(problem['detail']);
  const description = title && detail && title !== detail ? `${title}. ${detail}` : title || detail;
  const fallback =
    error.status === 0
      ? 'No pudimos conectar. Comprueba tu conexión e intenta de nuevo.'
      : error.status === 401
        ? 'Tu sesión no es válida. Inicia sesión nuevamente.'
        : error.status === 403
          ? 'No tienes permiso para realizar esta operación.'
          : 'No se pudo completar la solicitud. Inténtalo nuevamente.';
  const correlationId =
    nonEmptyText(problem['correlationId']) || nonEmptyText(error.headers.get('X-Correlation-Id'));
  return `${description || fallback}${correlationId ? ` · correlationId: ${correlationId}` : ''}`;
}

/**
 * Códigos del catálogo de errores que la vista ya presenta con un estado propio
 * (p. ej. FEATURE_NOT_IN_PLAN al abrir Webhooks en el plan Gratis). No se repiten en un toast.
 */
export const HANDLED_PROBLEMS = new HttpContextToken<readonly string[]>(() => []);

/** RFC 9457 y fallos de red de la API; las vistas siguen recibiendo el error original. */
export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isApiRequest(request.url, inject(BASE_PATH), inject(DOCUMENT).baseURI)) {
    return next(request);
  }
  const toast = inject(DsToastService);
  const handled = request.context.get(HANDLED_PROBLEMS);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        !handled.includes(nonEmptyText(problemBody(error.error)['code']))
      ) {
        toast.show(errorMessage(error));
      }
      return throwError(() => error);
    }),
  );
};
