import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsField,
  DsIcon,
  DsOption,
  DsSelect,
  DsToastService,
} from '../design-system';
import { Connector, connectors, connectorStatus } from '../data';

@Component({
  selector: 'dc-integrations',
  imports: [FormsModule, DsAlert, DsBadge, DsButton, DsCard, DsField, DsIcon, DsSelect],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Integraciones</h1>
        <p class="ds-muted">Conecta las fuentes que alimentan el perfil financiero de tus clientes.</p>
      </div>
      <ds-field label="Categoría">
        <ds-select [options]="categories" [ngModel]="category()" (ngModelChange)="category.set($event)" />
      </ds-field>
    </header>
    <ds-alert tone="warning" heading="Tu plan Gratis incluye 1 integración">
      Ya está en uso. Desconéctala o mejora a Pro para sumar más fuentes.
    </ds-alert>
    <div class="ds-grid">
      @for (connector of visible(); track connector.name) {
        <ds-card>
          <div class="ds-stack">
            <div class="ds-row ds-between">
              <span class="icon-tile"><ds-icon [name]="connector.icon" /></span>
              <ds-badge [tone]="status[connector.status].tone">{{ status[connector.status].label }}</ds-badge>
            </div>
            <div class="ds-stack-sm">
              <p class="ds-caption">{{ connector.category }}</p>
              <h2>{{ connector.name }}</h2>
              <p class="ds-muted">{{ connector.description }}</p>
            </div>
            <button dsButton [variant]="connector.status === 'connected' ? 'secondary' : 'primary'" [disabled]="connector.status === 'pro'" (click)="act(connector)">
              {{ connector.status === 'connected' ? 'Configurar' : 'Conectar' }}
            </button>
          </div>
        </ds-card>
      }
    </div>
  `,
})
export class Integrations {
  private readonly toast = inject(DsToastService);
  readonly status = connectorStatus;
  readonly category = signal('');
  readonly categories: DsOption<string>[] = [
    { value: '', label: 'Todas' },
    ...[...new Set(connectors.map((c) => c.category))].map((value) => ({ value, label: value })),
  ];
  readonly visible = computed(() =>
    connectors.filter((c) => !this.category() || c.category === this.category()),
  );

  act(connector: Connector): void {
    this.toast.show(`${connector.name}: configuración disponible próximamente.`);
  }
}
