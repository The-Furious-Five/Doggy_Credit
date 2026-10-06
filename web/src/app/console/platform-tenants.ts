import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminService, TenantActual, TenantPatchPlanEnum } from '../api';
import { DsAlert, DsBadge, DsButton, DsCard, DsField, DsOption, DsSelect, DsTable, DsToastService } from '../design-system';

@Component({
  selector: 'dc-platform-tenants',
  imports: [FormsModule, DsAlert, DsBadge, DsButton, DsCard, DsField, DsSelect, DsTable],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Administración de tenants</h1>
        <p class="ds-muted">Consulta instituciones y administra su plan y estado.</p>
      </div>
    </header>

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
            <thead><tr><th scope="col">Institución</th><th scope="col">NIT</th><th scope="col">Plan</th><th scope="col">Estado</th><th scope="col">Acciones</th></tr></thead>
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
                  <td><ds-badge [tone]="tenant.activo ? 'success' : 'danger'">{{ tenant.activo ? 'Activo' : 'Inactivo' }}</ds-badge></td>
                  <td>
                    <button dsButton variant="secondary" [busy]="busy().has(tenant.id)"
                      (click)="toggleActive(tenant)">{{ tenant.activo ? 'Desactivar' : 'Activar' }} institución</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </ds-card>
    }
  `,
})
export class PlatformTenants {
  private readonly admin = inject(AdminService);
  private readonly toast = inject(DsToastService);
  readonly tenants = signal<TenantActual[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly busy = signal(new Set<string>());
  readonly planOptions: DsOption<TenantPatchPlanEnum>[] = [
    { value: TenantPatchPlanEnum.Free, label: 'Gratis' },
    { value: TenantPatchPlanEnum.Pro, label: 'Pro' },
    { value: TenantPatchPlanEnum.Enterprise, label: 'Enterprise' },
  ];
  private readonly selectedPlans = signal<Record<string, TenantPatchPlanEnum>>({});

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

  async toggleActive(tenant: TenantActual): Promise<void> {
    await this.updateTenant(tenant, { activo: !tenant.activo }, tenant.activo ? 'Institución desactivada.' : 'Institución activada.');
  }

  private async updateTenant(tenant: TenantActual, patch: { activo?: boolean; plan?: TenantPatchPlanEnum }, success: string): Promise<void> {
    this.setBusy(tenant.id, true);
    try {
      const updated = await firstValueFrom(this.admin.actualizarTenant(tenant.id, patch));
      this.tenants.update((items) => items.map((item) => item.id === updated.id ? updated : item));
      this.choosePlan(updated.id, this.asPlan(updated.plan));
      this.toast.show(success);
    } catch {
      this.toast.show('No se pudo guardar. Revisa tu rol e inténtalo de nuevo.');
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
