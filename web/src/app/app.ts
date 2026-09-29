import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  DsAlert,
  DsBadge,
  DsBrand,
  DsButton,
  DsIcon,
  DsLink,
  DsLogo,
  DsSpinner,
} from './design-system';
import { OidcSecurityService } from 'angular-auth-oidc-client';

@Component({
  selector: 'dc-access-page',
  imports: [DsAlert, DsBadge, DsBrand, DsButton, DsIcon, DsLink, DsLogo, DsSpinner],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly oidc = inject(OidcSecurityService);
  readonly loading = signal(true);
  readonly authenticated = signal(false);
  readonly error = signal('');
  readonly name = signal('');
  readonly tenant = signal('');

  constructor() {
    this.oidc.isAuthenticated$.pipe(takeUntilDestroyed()).subscribe(({ isAuthenticated }) => {
      this.authenticated.set(isAuthenticated);
      if (!isAuthenticated) {
        this.name.set('');
        this.tenant.set('');
      }
    });
    this.oidc.checkAuth().subscribe({
      next: ({ isAuthenticated, userData }) => {
        this.authenticated.set(isAuthenticated);
        this.name.set(userData?.name || userData?.preferred_username || 'Usuario');
        this.tenant.set(userData?.tenant_id || '');
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('No pudimos verificar tu sesión. Intenta iniciar sesión nuevamente.');
      },
    });
  }
  login(): void {
    this.error.set('');
    this.oidc.authorize();
  }
  logout(): void {
    this.oidc.logoff().subscribe({
      error: () => this.error.set('No se pudo cerrar la sesión. Inténtalo nuevamente.'),
    });
  }
}
