import { Component, inject, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  DsBadge,
  DsButton,
  DsCard,
  DsDialog,
  DsField,
  DsIcon,
  DsInput,
  DsOption,
  DsProgress,
  DsSelect,
  DsTable,
  DsToastService,
} from '../design-system';

@Component({
  selector: 'dc-developers',
  imports: [FormsModule, DsBadge, DsButton, DsCard, DsDialog, DsField, DsIcon, DsInput, DsProgress, DsSelect, DsTable],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Desarrolladores</h1>
        <p class="ds-muted">Claves de API, webhooks y límites de uso de tu institución.</p>
      </div>
      <button dsButton (click)="dialog().show()"><ds-icon name="key" />Crear clave</button>
    </header>

    <ds-card>
      <div class="card-head"><h2>Claves de API</h2></div>
      <div class="ds-table-scroll" tabindex="0" aria-label="Claves de API">
        <table dsTable>
          <caption class="ds-sr-only">Claves de API activas</caption>
          <thead>
            <tr><th scope="col">Nombre</th><th scope="col">Clave</th><th scope="col">Entorno</th><th scope="col">Último uso</th></tr>
          </thead>
          <tbody>
            <tr><th scope="row">Originación web</th><td class="ds-mono">dc_live_••••8f2a</td><td><ds-badge tone="success">Producción</ds-badge></td><td>Hoy, 09:12</td></tr>
            <tr><th scope="row">Pruebas QA</th><td class="ds-mono">dc_test_••••41c0</td><td><ds-badge>Sandbox</ds-badge></td><td>28/09/2026</td></tr>
          </tbody>
        </table>
      </div>
    </ds-card>

    <div class="split">
      <ds-card>
        <div class="card-head"><h2>Primera llamada</h2><span class="ds-caption">REST · JSON</span></div>
        <pre class="code"><code>curl https://api.doggycredit.com/v1/evaluaciones \\
  -H "Authorization: Bearer dc_test_…" \\
  -d '{{ sample }}'</code></pre>
      </ds-card>
      <div class="ds-stack">
        <ds-card>
          <div class="card-head"><h2>Uso de la API</h2></div>
          <ds-progress label="Solicitudes por minuto" [value]="18" [max]="60" />
          <p class="ds-caption">Límite del plan Gratis: 60 por minuto.</p>
        </ds-card>
        <ds-card>
          <div class="card-head"><h2>Webhooks</h2><ds-badge tone="info">Plan Pro</ds-badge></div>
          <p class="ds-muted">Recibe eventos como evaluacion.completada o consentimiento.revocado.</p>
        </ds-card>
      </div>
    </div>

    <ds-dialog heading="Crear clave de API">
      <form class="ds-stack" (ngSubmit)="create()">
        <ds-field label="Nombre" hint="Para reconocerla después, p. ej. App móvil." required>
          <input dsInput name="keyName" [(ngModel)]="keyName" />
        </ds-field>
        <ds-field label="Entorno">
          <ds-select name="env" [options]="environments" [(ngModel)]="env" />
        </ds-field>
        <div class="ds-actions">
          <button dsButton variant="secondary" (click)="dialog().close()">Cancelar</button>
          <button dsButton type="submit">Crear clave</button>
        </div>
      </form>
    </ds-dialog>
  `,
})
export class Developers {
  private readonly toast = inject(DsToastService);
  readonly dialog = viewChild.required(DsDialog);
  readonly sample = '{"solicitante_id": "…", "monto": 15000, "plazo": 12}';
  readonly environments: DsOption<string>[] = [
    { value: 'test', label: 'Sandbox', description: 'Datos de prueba' },
    { value: 'live', label: 'Producción' },
  ];
  keyName = '';
  env = 'test';

  create(): void {
    this.dialog().close();
    this.toast.show('Clave creada. Cópiala ahora: no volveremos a mostrarla.');
  }
}
