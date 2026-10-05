import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  DsAlert,
  DsBadge,
  DsBrand,
  DsButton,
  DsIcon,
  DsIconName,
  DsLink,
  DsLogo,
  DsSpinner,
} from './design-system';
import { OidcSecurityService } from 'angular-auth-oidc-client';

@Component({
  selector: 'dc-access-page',
  imports: [RouterLink, DsAlert, DsBadge, DsBrand, DsButton, DsIcon, DsLink, DsLogo, DsSpinner],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly oidc = inject(OidcSecurityService);
  private readonly router = inject(Router);
  private readonly fromCallback = inject(ActivatedRoute).snapshot.url[0]?.path === 'callback';
  readonly loading = signal(true);
  readonly authenticated = signal(false);
  readonly error = signal('');
  readonly name = signal('');
  readonly tenant = signal('');
  readonly points: { icon: DsIconName; text: string }[] = [
    { icon: 'shield', text: 'Credenciales gestionadas por tu institución' },
    { icon: 'building', text: 'Un espacio de trabajo aislado por institución' },
    { icon: 'lock', text: 'Datos cifrados y consultas auditadas' },
  ];

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
        // Al volver de Keycloak con sesión, se entra directo a la consola.
        if (isAuthenticated && this.fromCallback) this.router.navigateByUrl('/app/resumen');
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
