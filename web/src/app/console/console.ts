import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { catchError, finalize, of } from 'rxjs';
import { TenantService } from '../api';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { canAccessAppRoute } from '../auth/permissions';
import { AuthSessionService } from '../auth/session.service';
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
  DsToastService,
} from '../design-system';

interface NavigationGroup {
  readonly title: string;
  readonly items: readonly { path: string; label: string; icon: DsIconName }[];
}

@Component({
  selector: 'dc-console',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    DsBadge,
    DsBrand,
    DsIcon,
    DsLink,
    DsMenu,
    DsProgress,
  ],
  styleUrl: './console.scss',
  template: `
    <a dsLink class="ds-skip" [routerLink]="path()" fragment="contenido">Saltar al contenido</a>
    <div class="console">
      <aside class="console-side">
        <a dsLink class="console-side__brand" routerLink="/" aria-label="DoggyCredit, inicio"
          ><ds-brand
        /></a>
        <nav class="console-nav" aria-label="Consola">
          @for (group of nav(); track group.title) {
            <p class="console-nav__group">{{ group.title }}</p>
            @for (item of group.items; track item.path) {
              <a dsLink [routerLink]="item.path" routerLinkActive ariaCurrentWhenActive="page">
                <ds-icon [name]="item.icon" />{{ item.label }}
              </a>
            }
          }
          @if (canAccess('/app/admin/tenants')) {
            <a dsLink routerLink="/app/admin/tenants" routerLinkActive ariaCurrentWhenActive="page">
              <ds-icon name="shield" />Administración de tenants
            </a>
          }
        </nav>
        <div class="console-plan ds-stack-sm">
          @if (tenant(); as currentTenant) {
            <strong
              >Plan
              {{
                currentTenant.plan === 'PRO'
                  ? 'Pro'
                  : currentTenant.plan === 'ENTERPRISE'
                    ? 'Enterprise'
                    : 'Gratis'
              }}</strong
            >
            @if (usage(); as currentUsage) {
              @if (currentUsage.limiteMensual === 0) {
                <p class="ds-caption">
                  {{ currentUsage.evaluacionesMes }} evaluaciones este mes · sin límite
                </p>
              } @else {
                <ds-progress
                  label="Evaluaciones del mes"
                  [value]="currentUsage.evaluacionesMes"
                  [max]="currentUsage.limiteMensual"
                />
                <p class="ds-caption">
                  {{ currentUsage.evaluacionesMes }} de {{ currentUsage.limiteMensual }} este mes
                </p>
              }
            } @else {
              <p class="ds-caption">
                {{ usage() === null ? 'Uso mensual no disponible' : 'Cargando uso mensual…' }}
              </p>
            }
            @if (currentTenant.plan === 'FREE' && canAccess('/app/configuracion')) {
              <a dsLink routerLink="/app/configuracion" fragment="plan"
                >Mejorar a Pro <ds-icon name="arrow"
              /></a>
            }
          } @else {
            <p class="ds-caption">
              {{ tenant() === null ? 'Plan no disponible' : 'Cargando plan…' }}
            </p>
          }
        </div>
      </aside>

      <div class="console-body">
        <header class="console-top">
          <div class="ds-row">
            <span class="console-top__tenant"
              ><ds-icon name="building" />{{
                tenant()?.nombre ||
                  (tenant() === null ? 'Institución no disponible' : 'Cargando institución…')
              }}</span
            >
            <ds-badge tone="warning">Datos simulados</ds-badge>
          </div>
          <div class="ds-row">
            <ds-menu
              label="Tema"
              icon="sun"
              variant="quiet"
              [items]="themes"
              (itemSelected)="setTheme($event)"
            />
            <span class="ds-caption">{{ user().name || 'Cargando usuario…' }}</span>
            <ds-menu
              [label]="'Cuenta de ' + (user().name || 'usuario')"
              icon="user"
              variant="secondary"
              [items]="account()"
              (itemSelected)="onAccount($event)"
            />
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
  private readonly auth = inject(AuthSessionService);
  private readonly toast = inject(DsToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly tenantService = inject(TenantService);
  readonly tenant = toSignal(
    this.tenantService.obtenerTenantActual().pipe(catchError(() => of(null))),
  );
  readonly usage = toSignal(this.tenantService.obtenerUsoTenant().pipe(catchError(() => of(null))));
  readonly user = this.auth.session;
  readonly loggingOut = signal(false);
  readonly path = () => this.router.url.split('#')[0];

  private readonly navigation: readonly NavigationGroup[] = [
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
  readonly nav = computed(() =>
    this.navigation
      .map((group) => ({
        ...group,
        items: group.items.filter((item) => this.canAccess(item.path)),
      }))
      .filter((group) => group.items.length > 0),
  );
  readonly themes: DsMenuItem[] = [
    { id: 'system', label: 'Según el sistema' },
    { id: 'light', label: 'Claro', icon: 'sun' },
    { id: 'dark', label: 'Oscuro', icon: 'moon' },
  ];
  readonly account = computed<DsMenuItem[]>(() => [
    ...(this.canAccess('/app/configuracion')
      ? [{ id: 'profile', label: 'Mi perfil', icon: 'user' as const, disabled: this.loggingOut() }]
      : []),
    {
      id: 'logout',
      label: this.loggingOut() ? 'Cerrando sesión…' : 'Cerrar sesión',
      icon: 'logout',
      tone: 'danger',
      disabled: this.loggingOut(),
    },
  ]);

  canAccess(path: string): boolean {
    const { isAuthenticated, roles } = this.auth.session();
    return isAuthenticated && canAccessAppRoute(path, roles);
  }

  setTheme(id: string): void {
    this.theme.preference.set(id as DsTheme);
  }
  onAccount(id: string): void {
    if (this.loggingOut()) return;
    if (id === 'profile' && this.canAccess('/app/configuracion')) {
      void this.router.navigateByUrl('/app/configuracion');
    } else if (id === 'logout') {
      this.loggingOut.set(true);
      this.auth
        .logout()
        .pipe(
          takeUntilDestroyed(this.destroyRef),
          finalize(() => this.loggingOut.set(false)),
        )
        .subscribe({
          error: () => this.toast.show('No se pudo cerrar la sesión. Inténtalo nuevamente.'),
        });
    }
  }
}
