import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withRouterConfig,
} from '@angular/router';
import { provideAuth } from 'angular-auth-oidc-client';
import { BASE_PATH } from './api/variables';
import { routes } from './app.routes';
import { apiErrorInterceptor } from './http/api-error.interceptor';
import { jwtInterceptor } from './http/jwt.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Los enlaces con fragmento (índice del catálogo, "Saltar al contenido") llevan a su sección.
    provideRouter(
      routes,
      withInMemoryScrolling({ anchorScrolling: 'enabled' }),
      // Volver a hacer clic en un enlace del menú (misma URL) debe volver a llevar a su sección.
      withRouterConfig({ onSameUrlNavigation: 'reload' }),
      withComponentInputBinding(),
    ),
    { provide: BASE_PATH, useValue: `${window.location.origin}/api/v1` },
    provideHttpClient(withInterceptors([apiErrorInterceptor, jwtInterceptor])),
    provideAuth({
      config: {
        authority: `${window.location.origin}/auth/realms/doggycredit`,
        clientId: 'doggycredit-web',
        responseType: 'code',
        scope: 'openid profile',
        redirectUrl: `${window.location.origin}/callback`,
        postLogoutRedirectUri: `${window.location.origin}/`,
        silentRenew: true,
        useRefreshToken: true,
      },
    }),
  ],
};
