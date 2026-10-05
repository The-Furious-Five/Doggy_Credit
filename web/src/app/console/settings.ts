import { Component, inject, linkedSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsField,
  DsIcon,
  DsInput,
  DsOption,
  DsSelect,
  DsSwitch,
  DsTab,
  DsTable,
  DsTabs,
  DsToastService,
} from '../design-system';
import { plans } from '../data';

@Component({
  selector: 'dc-settings',
  imports: [
    FormsModule,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsField,
    DsIcon,
    DsInput,
    DsSelect,
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
          <form class="ds-stack form" (ngSubmit)="toast.show('Cambios guardados.')">
            <ds-field label="Nombre de la institución" required>
              <input dsInput name="org" [(ngModel)]="org" />
            </ds-field>
            <ds-field label="Región de datos" hint="Dónde se almacenan los datos de tus clientes.">
              <ds-select name="region" [options]="regions" [(ngModel)]="region" />
            </ds-field>
            <dl class="ds-stack-sm">
              <dt class="ds-caption">Identificador de institución (tenant)</dt>
              <dd class="ds-mono">tnt_horizonte_7f3c</dd>
            </dl>
            <div class="ds-actions"><button dsButton type="submit">Guardar cambios</button></div>
          </form>
        </ds-card>
      </ds-tab>

      <ds-tab value="equipo" label="Equipo">
        <ds-card>
          <div class="card-head">
            <h2>Usuarios</h2>
            <button dsButton variant="secondary" (click)="toast.show('Invitaciones disponibles próximamente.')"><ds-icon name="plus" />Invitar</button>
          </div>
          <div class="ds-table-scroll" tabindex="0" aria-label="Usuarios">
            <table dsTable>
              <caption class="ds-sr-only">Usuarios de la institución</caption>
              <thead><tr><th scope="col">Nombre</th><th scope="col">Rol</th><th scope="col">Estado</th></tr></thead>
              <tbody>
                <tr><th scope="row">Ana Gutiérrez</th><td>Administradora</td><td><ds-badge tone="success">Activa</ds-badge></td></tr>
                <tr><th scope="row">Jorge Salinas</th><td>Analista de riesgo</td><td><ds-badge tone="success">Activo</ds-badge></td></tr>
                <tr><th scope="row">Carla Méndez</th><td>Oficial de crédito</td><td><ds-badge tone="warning">Invitación enviada</ds-badge></td></tr>
              </tbody>
            </table>
          </div>
          <p class="ds-caption">3 de 3 usuarios del plan Gratis.</p>
        </ds-card>
      </ds-tab>

      <ds-tab value="plan" label="Plan">
        <div class="ds-grid">
          @for (plan of plans; track plan.id) {
            <ds-card>
              <div class="ds-stack">
                <div class="ds-row ds-between">
                  <h2>{{ plan.name }}</h2>
                  @if (plan.id === 'free') {
                    <ds-badge tone="success">Plan actual</ds-badge>
                  }
                </div>
                <p><strong>{{ plan.price }}</strong> <span class="ds-caption">{{ plan.period }}</span></p>
                <ul class="list">
                  @for (feature of plan.features; track feature) {
                    <li>{{ feature }}<ds-icon name="check" /></li>
                  }
                </ul>
                @if (plan.id === 'pro') {
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
            <ds-switch [(ngModel)]="mfa">Exigir verificación en dos pasos a todo el equipo</ds-switch>
            <ds-switch [(ngModel)]="ipAllowlist">Permitir acceso solo desde redes autorizadas</ds-switch>
            <ds-switch [(ngModel)]="maskData">Ocultar documentos y montos a roles sin permiso</ds-switch>
            <ds-field label="Retención de datos de clientes">
              <ds-select [options]="retention" [(ngModel)]="retentionValue" />
            </ds-field>
            <ds-alert heading="Registro de auditoría activo">
              Cada consulta, cambio de permisos y exportación queda registrada con usuario y finalidad.
            </ds-alert>
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
  readonly plans = plans;
  readonly regions: DsOption<string>[] = [
    { value: 'sa', label: 'Sudamérica' },
    { value: 'us', label: 'Estados Unidos' },
  ];
  readonly retention: DsOption<string>[] = [
    { value: '12', label: '12 meses' },
    { value: '24', label: '24 meses' },
    { value: '60', label: '5 años (requisito regulatorio)' },
  ];
  org = 'Cooperativa Horizonte';
  region = 'sa';
  mfa = true;
  ipAllowlist = false;
  maskData = true;
  retentionValue = '60';
}
