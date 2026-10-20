import { Component, inject, signal, viewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminService, MetricasOperador, OperadorService, TenantActual, TenantPatchPlanEnum } from '../api';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsDialog,
  DsEmptyState,
  DsField,
  DsOption,
  DsSelect,
  DsSpinner,
  DsStat,
  DsTable,
  DsToastService,
} from '../design-system';

const numberFormat = new Intl.NumberFormat('es-BO');
// El período de las métricas es UTC (contrato de GET /operador/metricas).
const dates = new Intl.DateTimeFormat('es-BO', { timeZone: 'UTC' });
const planLabels: Record<string, string> = { FREE: 'Gratis', PRO: 'Pro', ENTERPRISE: 'Enterprise' };

@Component({
  selector: 'dc-platform-tenants',
  imports: [
    FormsModule,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsDialog,
    DsEmptyState,
    DsField,
    DsSelect,
    DsSpinner,
    DsStat,
    DsTable,
  ],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Administración de tenants</h1>
        <p class="ds-muted">Consulta instituciones y administra su plan y estado.</p>
      </div>
    </header>

    <section class="ds-stack" aria-labelledby="platform-metrics-heading">
      <div class="ds-row">
        <div class="card-head"><h2 id="platform-metrics-heading">Métricas de la plataforma</h2></div>
        @if (metrics.hasValue()) {
          <span class="ds-caption">{{ date(metrics.value().desde) }} – {{ date(metrics.value().hasta) }} (UTC)</span>
        }
      </div>
      @if (metrics.isLoading()) {
        <ds-spinner label="Cargando métricas…" />
      } @else if (metrics.error()) {
        <ds-alert heading="No se pudieron cargar las métricas" tone="danger">
          <p>La lista de instituciones sigue disponible. Vuelve a intentarlo en unos segundos.</p>
          <button dsButton variant="secondary" (click)="metrics.reload()">Reintentar</button>
        </ds-alert>
      } @else if (metrics.value(); as data) {
        <div class="kpis">
          <ds-card padding="compact"
            ><ds-stat label="Tenants" [value]="number(tenantCount(data))" [detail]="plansDetail(data)"
          /></ds-card>
          <ds-card padding="compact"
            ><ds-stat
              label="Activados"
              [value]="number(data.tenantsActivados)"
              detail="Con al menos una evaluación en el período"
          /></ds-card>
          <ds-card padding="compact"
            ><ds-stat
              label="Conversiones"
              [value]="number(data.conversionesFreeAPro)"
              detail="Subidas desde Gratis a Pro o Enterprise"
          /></ds-card>
          <ds-card padding="compact"
            ><ds-stat label="Evaluaciones" [value]="number(data.evaluaciones)" [detail]="usageDetail(data)"
          /></ds-card>
        </div>
        @if (data.evaluaciones === 0 && data.conversionesFreeAPro === 0) {
          <ds-empty-state
            heading="Sin actividad en este período"
            description="Ninguna institución registró evaluaciones ni subió de plan en el mes en curso."
          />
        }
      }
    </section>

    @if (error()) {
      <ds-alert heading="No se pudo cargar la lista" tone="danger">{{ error() }}</ds-alert>
    }
    @if (loading()) {
      <p class="ds-muted" role="status">Cargando instituciones…</p>
    } @else if (tenants().length === 0 && !error()) {
      <ds-card><p>No hay instituciones registradas.</p></ds-card>
    } @else if (tenants().length > 0) {
      <ds-card>
        <div class="ds-table-scroll" tabindex="0" aria-label="Instituciones registradas">
          <table dsTable>
            <caption class="ds-sr-only">Instituciones registradas y estado de sus planes</caption>
            <thead><tr><th scope="col">Institución</th><th scope="col">NIT</th><th scope="col">Plan</th><th scope="col">Consumo del mes</th><th scope="col">Estado</th><th scope="col">Acciones</th></tr></thead>
            <tbody>
              @for (tenant of tenants(); track tenant.id) {
                <tr>
                  <th scope="row">{{ tenant.nombre }}</th>
                  <td>{{ tenant.nit }}</td>
                  <td>
                    <div class="ds-stack-sm">
                      <ds-field [label]="'Plan para ' + tenant.nombre">
                        <ds-select [options]="planOptions" [ngModel]="planFor(tenant.id, tenant.plan)"
                          (ngModelChange)="choosePlan(tenant.id, $event)" [ngModelOptions]="{standalone: true}" />
                      </ds-field>
                      <button dsButton variant="secondary" [busy]="busy().has(tenant.id)"
                        [disabled]="!planChanged(tenant)"
                        (click)="savePlan(tenant)">Guardar plan</button>
                    </div>
                  </td>
                  <td>{{ usageText(tenant) }}</td>
                  <td><ds-badge [tone]="tenant.activo ? 'success' : 'danger'">{{ tenant.activo ? 'Activo' : 'Suspendido' }}</ds-badge></td>
                  <td>
                    <button dsButton variant="secondary" [busy]="busy().has(tenant.id)"
                      (click)="tenant.activo ? askSuspend(tenant) : reactivate(tenant)">{{ tenant.activo ? 'Suspender' : 'Reactivar' }} institución</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </ds-card>
    }

    <ds-dialog [heading]="'Suspender ' + (pending()?.nombre ?? 'institución')" (dismissed)="pending.set(null)">
      <div class="ds-stack">
        <p>
          Los usuarios y las claves de API de esta institución dejarán de operar de inmediato.
          No se borra ningún dato: al reactivarla todo vuelve a funcionar.
        </p>
        <div class="ds-actions">
          <button dsButton variant="secondary" (click)="dialog().close()">Cancelar</button>
          <button dsButton variant="danger" [busy]="!!pending() && busy().has(pending()!.id)" (click)="confirmSuspend()">Suspender institución</button>
        </div>
      </div>
    </ds-dialog>
  `,
})
export class PlatformTenants {
  private readonly admin = inject(AdminService);
  private readonly operator = inject(OperadorService);
  private readonly toast = inject(DsToastService);
  readonly tenants = signal<TenantActual[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly busy = signal(new Set<string>());
  readonly pending = signal<TenantActual | null>(null);
  readonly dialog = viewChild.required(DsDialog);
  readonly planOptions: DsOption<TenantPatchPlanEnum>[] = [
    { value: TenantPatchPlanEnum.Free, label: 'Gratis' },
    { value: TenantPatchPlanEnum.Pro, label: 'Pro' },
    { value: TenantPatchPlanEnum.Enterprise, label: 'Enterprise' },
  ];
  private readonly selectedPlans = signal<Record<string, TenantPatchPlanEnum>>({});
  /** D-14 (RF-ANA-04): sin fechas, el servidor responde el mes calendario UTC en curso. */
  readonly metrics = rxResource({ stream: () => this.operator.obtenerMetricasOperador() });

  constructor() {
    void this.refresh();
  }

  planFor(id: string, current: string): TenantPatchPlanEnum {
    return this.selectedPlans()[id] ?? this.asPlan(current);
  }

  choosePlan(id: string, plan: TenantPatchPlanEnum): void {
    this.selectedPlans.update((selected) => ({ ...selected, [id]: plan }));
  }

  planChanged(tenant: TenantActual): boolean {
    return String(this.planFor(tenant.id, tenant.plan)) !== tenant.plan;
  }

  async savePlan(tenant: TenantActual): Promise<void> {
    const plan = this.planFor(tenant.id, tenant.plan);
    if (String(plan) === tenant.plan) return;
    await this.updateTenant(tenant, { plan }, 'Plan actualizado.');
  }

  askSuspend(tenant: TenantActual): void {
    this.pending.set(tenant);
    this.dialog().show();
  }

  async confirmSuspend(): Promise<void> {
    const tenant = this.pending();
    if (!tenant) return;
    if (await this.updateTenant(tenant, { activo: false }, 'Institución suspendida.')) this.dialog().close();
  }

  async reactivate(tenant: TenantActual): Promise<void> {
    await this.updateTenant(tenant, { activo: true }, 'Institución reactivada.');
  }

  number(value: number): string {
    return numberFormat.format(value);
  }

  date(iso: string): string {
    return dates.format(new Date(iso + 'T00:00:00Z'));
  }

  tenantCount(data: MetricasOperador): number {
    return data.tenantsActivos + data.tenantsSuspendidos;
  }

  /** `Gratis 3 · Pro 2 · Enterprise 1 · 1 suspendido`. */
  plansDetail(data: MetricasOperador): string {
    const plans = data.tenantsPorPlan.map((row) => `${planLabels[row.plan] ?? row.plan} ${this.number(row.cantidad)}`);
    const suspended = data.tenantsSuspendidos === 1 ? '1 suspendido' : `${this.number(data.tenantsSuspendidos)} suspendidos`;
    return [...plans, suspended].join(' · ');
  }

  /** Consumo por plan actual; un servidor anterior a D-14 no envía el desglose. */
  usageDetail(data: MetricasOperador): string {
    const rows = data.tenantsPorPlan.filter((row) => row.evaluaciones != null);
    if (rows.length === 0) return 'En el período';
    return rows.map((row) => `${planLabels[row.plan] ?? row.plan} ${this.number(row.evaluaciones ?? 0)}`).join(' · ');
  }

  /** `X de Y`, o `X · sin límite` cuando el plan no tiene cuota (límite 0). */
  usageText(tenant: TenantActual): string {
    const used = tenant.evaluacionesMes ?? 0;
    const limit = tenant.limiteMensual ?? 0;
    return limit > 0 ? `${used} de ${limit}` : `${used} · sin límite`;
  }

  private async updateTenant(tenant: TenantActual, patch: { activo?: boolean; plan?: TenantPatchPlanEnum }, success: string): Promise<boolean> {
    this.setBusy(tenant.id, true);
    try {
      const updated = await firstValueFrom(this.admin.actualizarTenant(tenant.id, patch));
      this.tenants.update((items) => items.map((item) => item.id === updated.id ? updated : item));
      this.choosePlan(updated.id, this.asPlan(updated.plan));
      this.toast.show(success);
      return true;
    } catch {
      this.toast.show('No se pudo guardar. Revisa tu rol e inténtalo de nuevo.');
      return false;
    } finally {
      this.setBusy(tenant.id, false);
    }
  }

  private async refresh(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.tenants.set(await firstValueFrom(this.admin.listarTenants()));
    } catch {
      this.error.set('Tu sesión necesita el rol PLATFORM_ADMIN para ver y administrar tenants.');
    } finally {
      this.loading.set(false);
    }
  }

  private setBusy(id: string, value: boolean): void {
    this.busy.update((current) => {
      const next = new Set(current);
      if (value) next.add(id); else next.delete(id);
      return next;
    });
  }

  private asPlan(plan: string): TenantPatchPlanEnum {
    if (plan === 'PRO') return TenantPatchPlanEnum.Pro;
    if (plan === 'ENTERPRISE') return TenantPatchPlanEnum.Enterprise;
    return TenantPatchPlanEnum.Free;
  }
}
