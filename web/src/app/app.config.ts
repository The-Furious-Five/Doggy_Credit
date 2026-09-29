import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { authInterceptor, provideAuth } from 'angular-auth-oidc-client';
import { BASE_PATH } from './api/variables';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Los enlaces con fragmento (índice del catálogo, "Saltar al contenido") llevan a su sección.
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' })),
    { provide: BASE_PATH, useValue: `${window.location.origin}/api/v1` },
    provideHttpClient(withInterceptors([authInterceptor()])),
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
        secureRoutes: [`${window.location.origin}/api/v1/`],
      },
    }),
  ],
};
