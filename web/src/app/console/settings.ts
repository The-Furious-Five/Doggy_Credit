import { Component, inject, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { catchError, firstValueFrom, of } from 'rxjs';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsField,
  DsIcon,
  DsInput,
  DsProgress,
  DsSwitch,
  DsTab,
  DsTable,
  DsTabs,
  DsToastService,
} from '../design-system';
import { plans } from '../data';
import { TenantService } from '../api';
import { AuditLog } from './audit-log';

@Component({
  selector: 'dc-settings',
  imports: [
    FormsModule,
    AuditLog,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsField,
    DsIcon,
    DsInput,
    DsProgress,
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
              <dd>{{ tenant()?.pais }} · {{ tenant()?.moneda }}</dd>
            </dl>
            <dl class="ds-stack-sm">
              <dt class="ds-caption">Identificador de institución (tenant)</dt>
              <dd class="ds-mono">{{ tenant()?.id ?? 'Cargando…' }}</dd>
            </dl>
            <div class="ds-actions"><button dsButton type="submit" [busy]="savingOrganization">Guardar cambios</button></div>
          </form>
        </ds-card>
      </ds-tab>

      <ds-tab value="equipo" label="Equipo">
        <ds-card>
          <div class="card-head">
            <h2>Usuarios con acceso</h2>
          </div>
          @if (teamError()) {
            <ds-alert heading="Equipo no disponible" tone="warning">
              No se pudo consultar el directorio de identidad. Recarga la página o revisa la conexión de Keycloak.
            </ds-alert>
          } @else {
            <div class="ds-table-scroll" tabindex="0" aria-label="Usuarios">
              <table dsTable>
                <caption class="ds-sr-only">Usuarios de la institución</caption>
                <thead><tr><th scope="col">Nombre</th><th scope="col">Rol</th><th scope="col">Estado</th></tr></thead>
                <tbody>
                  @for (member of team(); track member.email ?? member.nombre) {
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
        @if (usage(); as quota) {
          <ds-card>
            <div class="ds-stack-sm">
              <h2>Uso de evaluaciones este mes</h2>
              @if (quota.limiteMensual > 0) {
                <p>{{ quota.evaluacionesMes }} de {{ quota.limiteMensual }} evaluaciones utilizadas</p>
                <ds-progress label="Evaluaciones mensuales" [value]="quota.evaluacionesMes" [max]="quota.limiteMensual" />
              } @else {
                <p>{{ quota.evaluacionesMes }} evaluaciones utilizadas. El plan actual no tiene límite mensual.</p>
              }
            </div>
          </ds-card>
        } @else if (usageUnavailable()) {
          <ds-alert heading="No se pudo consultar el uso" tone="warning">
            Comprueba la conexión e inténtalo de nuevo más tarde.
          </ds-alert>
        }
        <div class="ds-grid">
          @for (plan of plans; track plan.id) {
            <ds-card>
              <div class="ds-stack">
                <div class="ds-row ds-between">
                  <h2>{{ plan.name }}</h2>
                  @if (tenant()?.plan === plan.id.toUpperCase()) {
                    <ds-badge tone="success">Plan actual</ds-badge>
                  }
                </div>
                <p><strong>{{ plan.price }}</strong> <span class="ds-caption">{{ plan.period }}</span></p>
                <ul class="list">
                  @for (feature of plan.features; track feature) {
                    <li>{{ feature }}<ds-icon name="check" /></li>
                  }
                </ul>
                @if (plan.id === 'pro' && tenant()?.plan !== 'PRO') {
                  <button dsButton (click)="toast.show('Te contactaremos para activar Pro.')">Mejorar a Pro</button>
                }
              </div>
            </ds-card>
          }
        </div>
      </ds-tab>

      <ds-tab value="seguridad" label="Seguridad">
        <ds-card>
          <div class="ds-stack">
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
  readonly toast = inject(DsToastService);
  private readonly fragment = toSignal(inject(ActivatedRoute).fragment);
  readonly tab = linkedSignal(() => this.fragment() ?? 'organizacion');

  private readonly tenantService = inject(TenantService);
  readonly usageUnavailable = signal(false);
  readonly usage = toSignal(this.tenantService.obtenerUsoTenant().pipe(catchError(() => {
    this.usageUnavailable.set(true);
    return of(null);
  })), { initialValue: null });
  readonly tenant = toSignal(this.tenantService.obtenerTenantActual());
  readonly teamError = signal(false);
  readonly team = toSignal(this.tenantService.listarEquipo().pipe(catchError(() => {
    this.teamError.set(true);
    return of([]);
  })), { initialValue: [] });
  readonly preferences = toSignal(this.tenantService.obtenerPreferenciasTenant());
  readonly maskData = linkedSignal(() => this.preferences()?.enmascararDatos ?? true);

  readonly plans = plans;
  readonly org = linkedSignal(() => this.tenant()?.nombre ?? '');
  savingOrganization = false;
  savingPreferences = false;

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
