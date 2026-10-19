import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, firstValueFrom, of } from 'rxjs';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsEmptyState,
  DsField,
  DsInput,
  DsLink,
  DsProgress,
  DsSpinner,
  DsSwitch,
  DsTab,
  DsTable,
  DsTabs,
  DsToastService,
} from '../design-system';
import { MiembroEquipo, TenantActualFuncionalidadesEnum, TenantActualPlanEnum, TenantService } from '../api';
import { AuthSessionService } from '../auth/session.service';
import { AuditLog } from './audit-log';
import { TeamInvite } from './team-invite';
import { TenantUsageService } from './tenant-usage.service';

const PLAN_NAMES: Record<TenantActualPlanEnum, string> = {
  [TenantActualPlanEnum.Free]: 'Gratis',
  [TenantActualPlanEnum.Pro]: 'Pro',
  [TenantActualPlanEnum.Enterprise]: 'Enterprise',
};

/** A-09 (LOS-70): nombres de las funciones que controla el plan (billing.Feature). */
const FEATURES: { id: TenantActualFuncionalidadesEnum; label: string }[] = [
  { id: TenantActualFuncionalidadesEnum.ScoringAvanzado, label: 'Scoring avanzado y explicable' },
  { id: TenantActualFuncionalidadesEnum.Recomendaciones, label: 'Recomendaciones de productos' },
  { id: TenantActualFuncionalidadesEnum.Analytics, label: 'Analítica de cartera' },
  { id: TenantActualFuncionalidadesEnum.Webhooks, label: 'Webhooks' },
  { id: TenantActualFuncionalidadesEnum.ApiProduccion, label: 'API de producción' },
];

@Component({
  selector: 'dc-settings',
  imports: [
    FormsModule,
    RouterLink,
    AuditLog,
    TeamInvite,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsEmptyState,
    DsField,
    DsInput,
    DsLink,
    DsProgress,
    DsSpinner,
    DsSwitch,
    DsTab,
    DsTable,
    DsTabs,
  ],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Configuración</h1>
        <p class="ds-muted">Tu institución, tu equipo, tu plan y la seguridad de los datos.</p>
      </div>
    </header>

    <ds-tabs label="Secciones de configuración" [(selected)]="tab">
      <ds-tab value="organizacion" label="Organización">
        <ds-card>
          <form class="ds-stack form" (ngSubmit)="saveOrganization()">
            <ds-field label="Nombre de la institución" required>
              <input dsInput name="org" [(ngModel)]="org" minlength="2" maxlength="100" />
            </ds-field>
            <dl class="ds-stack-sm">
              <dt class="ds-caption">País y moneda de operación</dt>
              <dd>{{ tenant() ? tenant()!.pais + ' · ' + tenant()!.moneda : tenantStatus() }}</dd>
            </dl>
            <dl class="ds-stack-sm">
              <dt class="ds-caption">Identificador de institución (tenant)</dt>
              <dd class="ds-mono">{{ tenant()?.id ?? tenantStatus() }}</dd>
            </dl>
            <div class="ds-actions"><button dsButton type="submit" [busy]="savingOrganization">Guardar cambios</button></div>
          </form>
        </ds-card>
      </ds-tab>

      <ds-tab value="equipo" label="Equipo">
        <ds-card>
          <div class="card-head">
            <h2>Usuarios con acceso</h2>
            @if (isTenantAdmin()) {
              <dc-team-invite (invited)="loadTeam()" />
            }
          </div>
          @if (teamError()) {
            <ds-alert heading="Equipo no disponible" tone="warning">
              No se pudo consultar el directorio de identidad. Revisa la conexión de Keycloak e inténtalo de nuevo.
              <button dsButton variant="secondary" (click)="loadTeam()">Reintentar</button>
            </ds-alert>
          } @else if (team() === undefined) {
            <ds-spinner label="Cargando usuarios…" />
          } @else if (team()!.length === 0) {
            <ds-empty-state
              icon="users"
              heading="Todavía no hay usuarios"
              description="Las personas que invites aparecerán aquí con su rol y su estado."
            />
          } @else {
            <div class="ds-table-scroll" tabindex="0" aria-label="Usuarios">
              <table dsTable>
                <caption class="ds-sr-only">Usuarios de la institución</caption>
                <thead><tr><th scope="col">Nombre</th><th scope="col">Rol</th><th scope="col">Estado</th></tr></thead>
                <tbody>
                  @for (member of team()!; track member.email ?? member.nombre) {
                    <tr>
                      <th scope="row">{{ member.nombre }}@if (member.email) { <span class="ds-caption"> · {{ member.email }}</span> }</th>
                      <td>{{ member.rol }}</td>
                      <td><ds-badge [tone]="member.estado === 'ACTIVO' ? 'success' : member.estado === 'INVITADO' ? 'warning' : 'neutral'">{{ member.estado === 'ACTIVO' ? 'Activo/a' : member.estado === 'INVITADO' ? 'Invitación enviada' : 'Desactivado/a' }}</ds-badge></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
          <p class="ds-caption">Usuarios, roles y estado sincronizados desde el directorio de identidad.</p>
        </ds-card>
      </ds-tab>

      <ds-tab value="plan" label="Plan">
        <ds-card>
          @if (tenant(); as current) {
            <div class="ds-stack">
              <div class="ds-row ds-between">
                <h2>Plan {{ planNames[current.plan] }}</h2>
                <ds-badge tone="success">Plan actual</ds-badge>
              </div>
              @if (usage(); as quota) {
                @if (quota.limiteMensual > 0) {
                  <p>{{ quota.evaluacionesMes }} de {{ quota.limiteMensual }} evaluaciones utilizadas este mes</p>
                  <ds-progress label="Evaluaciones mensuales" [value]="quota.evaluacionesMes" [max]="quota.limiteMensual" />
                } @else {
                  <p>{{ quota.evaluacionesMes }} evaluaciones utilizadas este mes. Tu plan no tiene límite mensual.</p>
                }
              } @else if (usageUnavailable()) {
                <ds-alert heading="No se pudo consultar el uso" tone="warning">
                  Comprueba la conexión e inténtalo de nuevo más tarde.
                </ds-alert>
              } @else {
                <ds-spinner label="Cargando uso mensual…" />
              }
              @if (current.funcionalidades; as included) {
                <dl class="ds-stack-sm">
                  <dt class="ds-caption">Usuarios</dt>
                  <dd>{{ limit(current.limiteUsuarios, 'Hasta ') }}</dd>
                  <dt class="ds-caption">Integraciones activas</dt>
                  <dd>{{ limit(current.limiteIntegraciones, 'Hasta ') }}</dd>
                </dl>
                <div class="ds-table-scroll" tabindex="0" aria-label="Funciones del plan">
                  <table dsTable>
                    <caption>Funciones del plan</caption>
                    <thead><tr><th scope="col">Función</th><th scope="col">Estado</th></tr></thead>
                    <tbody>
                      @for (feature of features; track feature.id) {
                        <tr>
                          <th scope="row">{{ feature.label }}</th>
                          <td>
                            @if (included.includes(feature.id)) {
                              <ds-badge tone="success">Incluida</ds-badge>
                            } @else {
                              <ds-badge>Requiere plan Pro</ds-badge>
                            }
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
              <p class="ds-muted">El equipo de DoggyCredit activa los cambios de plan de tu institución.</p>
              <div class="ds-actions">
                <a dsLink appearance="secondary" routerLink="/" fragment="planes">Comparar planes</a>
              </div>
            </div>
          } @else if (tenantUnavailable()) {
            <ds-alert heading="No se pudo consultar el plan" tone="warning">
              Comprueba la conexión e inténtalo de nuevo más tarde.
            </ds-alert>
          } @else {
            <ds-spinner label="Cargando plan…" />
          }
        </ds-card>
      </ds-tab>

      <ds-tab value="seguridad" label="Seguridad">
        <ds-card>
          <div class="ds-stack">
            @if (preferencesUnavailable()) {
              <ds-alert heading="No se pudo consultar la preferencia" tone="warning">
                Se muestra el valor por defecto. Recarga la página antes de cambiarla.
              </ds-alert>
            }
            <form class="ds-stack" (ngSubmit)="saveSecurityPreferences()">
              <ds-switch [ngModel]="maskData()" (ngModelChange)="maskData.set($event)" name="maskData">
                Ocultar documentos y montos a roles sin permiso
              </ds-switch>
              <div class="ds-actions">
                <button dsButton type="submit" [busy]="savingPreferences">Guardar preferencia</button>
              </div>
            </form>
            <ds-alert heading="Registro de auditoría activo">
              Cada consulta, cambio de permisos y exportación queda registrada con usuario y finalidad.
            </ds-alert>
            <dc-audit-log />
          </div>
        </ds-card>
      </ds-tab>
    </ds-tabs>
  `,
})
export class Settings {
  private readonly toast = inject(DsToastService);
  private readonly fragment = toSignal(inject(ActivatedRoute).fragment);
  readonly tab = linkedSignal(() => this.fragment() ?? 'organizacion');

  private readonly tenantService = inject(TenantService);
  private readonly usageService = inject(TenantUsageService);
  readonly usage = this.usageService.usage;
  readonly usageUnavailable = computed(() => this.usage() === null);
  readonly tenantUnavailable = signal(false);
  readonly tenant = toSignal(this.tenantService.obtenerTenantActual().pipe(catchError(() => {
    this.tenantUnavailable.set(true);
    return of(null);
  })));
  private readonly session = inject(AuthSessionService).session;
  readonly isTenantAdmin = computed(() => this.session().roles.includes('TENANT_ADMIN'));
  readonly teamError = signal(false);
  /** undefined mientras carga; la lista vacía es un estado propio. */
  readonly team = signal<MiembroEquipo[] | undefined>(undefined);
  readonly preferencesUnavailable = signal(false);
  readonly preferences = toSignal(this.tenantService.obtenerPreferenciasTenant().pipe(catchError(() => {
    this.preferencesUnavailable.set(true);
    return of(null);
  })));
  readonly maskData = linkedSignal(() => this.preferences()?.enmascararDatos ?? true);

  readonly planNames = PLAN_NAMES;
  readonly features = FEATURES;
  readonly org = linkedSignal(() => this.tenant()?.nombre ?? '');
  savingOrganization = false;
  savingPreferences = false;

  constructor() {
    // Si la consola ya lo había consultado, la pestaña Plan muestra el uso al día.
    if (this.usage() !== undefined) this.usageService.refresh();
    this.loadTeam();
  }

  loadTeam(): void {
    this.teamError.set(false);
    this.team.set(undefined);
    this.tenantService.listarEquipo().subscribe({
      next: (members) => this.team.set(members),
      error: () => this.teamError.set(true),
    });
  }

  /** 0 en la API significa sin límite. */
  limit(value: number | undefined, prefix: string): string {
    return value === undefined ? '—' : value === 0 ? 'Sin límite' : `${prefix}${value}`;
  }

  tenantStatus(): string {
    return this.tenantUnavailable() ? 'No disponible' : 'Cargando…';
  }

  async saveOrganization(): Promise<void> {
    this.savingOrganization = true;
    try {
      const updated = await firstValueFrom(this.tenantService.actualizarOrganizacionTenant({ nombre: this.org() }));
      this.org.set(updated.nombre);
      this.toast.show('Nombre de la institución actualizado.');
    } catch {
      this.toast.show('No se pudo guardar. Revisa tus permisos e inténtalo de nuevo.');
    } finally {
      this.savingOrganization = false;
    }
  }

  async saveSecurityPreferences(): Promise<void> {
    this.savingPreferences = true;
    try {
      const updated = await firstValueFrom(this.tenantService.actualizarPreferenciasTenant({
        enmascararDatos: this.maskData(),
      }));
      this.maskData.set(updated.enmascararDatos);
      this.toast.show('Preferencia de enmascarado actualizada.');
    } catch {
      this.toast.show('No se pudo guardar. Revisa tus permisos e inténtalo de nuevo.');
    } finally {
      this.savingPreferences = false;
    }
  }
}
