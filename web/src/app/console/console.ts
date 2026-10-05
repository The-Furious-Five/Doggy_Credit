import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { map } from 'rxjs';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import {
  DsBadge,
  DsBrand,
  DsIcon,
  DsIconName,
  DsLink,
  DsMenu,
  DsMenuItem,
  DsProgress,
  DsTheme,
  DsThemeService,
} from '../design-system';

@Component({
  selector: 'dc-console',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, DsBadge, DsBrand, DsIcon, DsLink, DsMenu, DsProgress],
  styleUrl: './console.scss',
  template: `
    <a dsLink class="ds-skip" [routerLink]="path()" fragment="contenido">Saltar al contenido</a>
    <div class="console">
      <aside class="console-side">
        <a dsLink class="console-side__brand" routerLink="/" aria-label="DoggyCredit, inicio"><ds-brand /></a>
        <nav class="console-nav" aria-label="Consola">
          @for (group of nav; track group.title) {
            <p class="console-nav__group">{{ group.title }}</p>
            @for (item of group.items; track item.path) {
              <a dsLink [routerLink]="item.path" routerLinkActive ariaCurrentWhenActive="page">
                <ds-icon [name]="item.icon" />{{ item.label }}
              </a>
            }
          }
        </nav>
        <div class="console-plan ds-stack-sm">
          <strong>Plan Gratis</strong>
          <ds-progress label="Consultas" [value]="342" [max]="500" />
          <p class="ds-caption">342 de 500 este mes</p>
          <a dsLink routerLink="/app/configuracion" fragment="plan">Mejorar a Pro <ds-icon name="arrow" /></a>
        </div>
      </aside>

      <div class="console-body">
        <header class="console-top">
          <div class="ds-row">
            <span class="console-top__tenant"><ds-icon name="building" />{{ user().tenant }}</span>
            <ds-badge tone="warning">Datos simulados</ds-badge>
          </div>
          <div class="ds-row">
            <ds-menu label="Tema" icon="sun" variant="quiet" [items]="themes" (itemSelected)="setTheme($event)" />
            <ds-menu [label]="'Cuenta de ' + user().name" icon="user" variant="secondary" [items]="account" (itemSelected)="onAccount($event)" />
          </div>
        </header>
        <main id="contenido" class="console-main" tabindex="-1">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class Console {
  private readonly router = inject(Router);
  private readonly theme = inject(DsThemeService);
  private readonly oidc = inject(OidcSecurityService);
  readonly user = toSignal(
    this.oidc.userData$.pipe(
      map(({ userData }) => ({
        name: userData?.name || userData?.preferred_username || 'Usuario',
        tenant: userData?.tenant_id || 'Institución',
      })),
    ),
    { initialValue: { name: 'Usuario', tenant: 'Institución' } },
  );
  readonly path = () => this.router.url.split('#')[0];

  readonly nav: { title: string; items: { path: string; label: string; icon: DsIconName }[] }[] = [
    {
      title: 'Operación',
      items: [
        { path: '/app/resumen', label: 'Resumen', icon: 'grid' },
        { path: '/app/clientes', label: 'Clientes', icon: 'users' },
        { path: '/app/evaluaciones/nueva', label: 'Nueva evaluación', icon: 'plus' },
      ],
    },
    {
      title: 'Decisiones',
      items: [
        { path: '/app/productos', label: 'Productos', icon: 'coin' },
        { path: '/app/politicas', label: 'Políticas', icon: 'bolt' },
        { path: '/app/analitica', label: 'Analítica', icon: 'chart' },
      ],
    },
    {
      title: 'Plataforma',
      items: [
        { path: '/app/integraciones', label: 'Integraciones', icon: 'plug' },
        { path: '/app/desarrolladores', label: 'Desarrolladores', icon: 'code' },
        { path: '/app/configuracion', label: 'Configuración', icon: 'sliders' },
      ],
    },
  ];
  readonly themes: DsMenuItem[] = [
    { id: 'system', label: 'Según el sistema' },
    { id: 'light', label: 'Claro', icon: 'sun' },
    { id: 'dark', label: 'Oscuro', icon: 'moon' },
  ];
  readonly account: DsMenuItem[] = [
    { id: 'profile', label: 'Mi perfil', icon: 'user' },
    { id: 'logout', label: 'Cerrar sesión', icon: 'logout', tone: 'danger' },
  ];

  setTheme(id: string): void {
    this.theme.preference.set(id as DsTheme);
  }
  onAccount(id: string): void {
    if (id === 'logout') this.oidc.logoff().subscribe();
    else this.router.navigateByUrl('/app/configuracion');
  }
}
