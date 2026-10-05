import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsIcon,
  DsLink,
  DsMenu,
  DsMenuItem,
  DsProgress,
  DsStat,
  DsTab,
  DsTable,
  DsTabs,
  DsToastService,
} from '../design-system';
import { clients, productKinds, products } from '../data';
import { SensitiveDataService } from './sensitive-data.service';

@Component({
  selector: 'dc-client-profile',
  imports: [
    RouterLink,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsIcon,
    DsLink,
    DsMenu,
    DsProgress,
    DsStat,
    DsTab,
    DsTable,
    DsTabs,
  ],
  styleUrl: './page.scss',
  template: `
    <a dsLink routerLink="/app/clientes"><ds-icon name="chevron-left" />Clientes</a>
    <header class="page-header">
      <div class="ds-stack-sm">
        <div class="ds-row">
          <ds-badge>{{ client().segment }}</ds-badge>
          <ds-badge tone="success">Consentimiento vigente</ds-badge>
        </div>
        <h1>{{ client().name }}</h1>
        <p class="ds-muted">
          Documento <span class="ds-mono">{{ sensitive.document(client().document) }}</span> · Actualizado {{ client().updated }}
        </p>
      </div>
      <div class="ds-row">
        <ds-menu label="Más acciones" icon="more" [items]="actions" (itemSelected)="onAction($event)" />
        <a dsLink appearance="primary" routerLink="/app/evaluaciones/nueva">Reevaluar</a>
      </div>
    </header>

    <ds-tabs label="Perfil del cliente">
      <ds-tab value="resumen" label="Resumen">
        <div class="ds-stack">
          <div class="kpis">
            <ds-card padding="compact"><ds-stat label="Ingreso mensual" [value]="sensitive.amount('Bs 9.800')" detail="Promedio de 6 meses" /></ds-card>
            <ds-card padding="compact"><ds-stat label="Deuda vigente" [value]="sensitive.amount('Bs 21.400')" detail="3 obligaciones" /></ds-card>
            <ds-card padding="compact"><ds-stat label="Endeudamiento" value="34 %" detail="Cuota sobre ingreso" /></ds-card>
            <ds-card padding="compact"><ds-stat label="Antigüedad" value="6 años" detail="En el sistema financiero" /></ds-card>
          </div>
          <ds-card>
            <div class="card-head"><h2>Necesidad detectada</h2><ds-badge tone="info">Confianza alta</ds-badge></div>
            <p class="ds-lead">{{ client().need }}</p>
            <p class="ds-muted">
              Inferida a partir de pagos recurrentes, estacionalidad de ingresos y destino declarado en
              solicitudes anteriores.
            </p>
          </ds-card>
        </div>
      </ds-tab>

      <ds-tab value="score" label="Score">
        <div class="split">
          <ds-card>
            <div class="ds-stack">
              <div class="ds-stack-sm">
                <p class="ds-caption">Score DoggyCredit</p>
                <p class="score">{{ client().score }}</p>
                <ds-badge [tone]="client().score >= 650 ? 'success' : 'warning'">
                  {{ client().score >= 650 ? 'Riesgo bajo' : 'Riesgo medio' }}
                </ds-badge>
              </div>
              <dl class="facts">
                <div><dt class="ds-caption">Modelo</dt><dd class="ds-mono">scoring-v2.3</dd></div>
                <div><dt class="ds-caption">Calculado</dt><dd>{{ client().updated }}</dd></div>
              </dl>
            </div>
          </ds-card>
          <ds-card>
            <div class="card-head"><h2>Factores</h2><span class="ds-caption">Mayor es mejor</span></div>
            <div class="bars">
              <ds-progress label="Historial de pagos" [value]="88" />
              <ds-progress label="Capacidad de pago" [value]="64" />
              <ds-progress label="Estabilidad de ingresos" [value]="71" />
              <ds-progress label="Uso de crédito disponible" [value]="52" />
            </div>
          </ds-card>
        </div>
        <ds-alert heading="El score orienta, no decide">
          Revisa los factores y las fuentes antes de aprobar o rechazar una solicitud.
        </ds-alert>
      </ds-tab>

      <ds-tab value="recomendaciones" label="Recomendaciones">
        <div class="ds-grid">
          @for (kind of productKinds; track kind) {
            <ds-card>
              <div class="ds-stack-sm">
                <div class="ds-row ds-between">
                  <span class="icon-tile"><ds-icon [name]="products[kind].icon" /></span>
                  @if (kind === client().product) {
                    <ds-badge tone="success">Mejor ajuste</ds-badge>
                  }
                </div>
                <h3>{{ products[kind].name }}</h3>
                <p class="ds-muted">{{ products[kind].summary }}</p>
                <dl class="facts">
                  <div><dt class="ds-caption">Monto sugerido</dt><dd>{{ sensitive.amount(products[kind].range) }}</dd></div>
                  <div><dt class="ds-caption">Plazo</dt><dd>{{ products[kind].term }}</dd></div>
                </dl>
                <button dsButton [variant]="kind === client().product ? 'primary' : 'secondary'" (click)="propose(products[kind].name)">
                  Preparar propuesta
                </button>
              </div>
            </ds-card>
          }
        </div>
      </ds-tab>

      <ds-tab value="fuentes" label="Fuentes">
        <ds-card>
          <div class="ds-table-scroll" tabindex="0" aria-label="Fuentes de datos">
            <table dsTable>
              <caption>Fuentes consultadas con consentimiento del cliente</caption>
              <thead>
                <tr><th scope="col">Fuente</th><th scope="col">Datos</th><th scope="col">Consultada</th><th scope="col">Estado</th></tr>
              </thead>
              <tbody>
                <tr><th scope="row">Buró de crédito nacional</th><td>Historial y deudas</td><td>{{ client().updated }}</td><td><ds-badge tone="success">Completa</ds-badge></td></tr>
                <tr><th scope="row">Core bancario</th><td>Cuentas y movimientos</td><td>{{ client().updated }}</td><td><ds-badge tone="success">Completa</ds-badge></td></tr>
                <tr><th scope="row">Billetera móvil</th><td>Flujos de ingresos</td><td>—</td><td><ds-badge>No conectada</ds-badge></td></tr>
              </tbody>
            </table>
          </div>
        </ds-card>
      </ds-tab>
    </ds-tabs>
  `,
})
export class ClientProfile {
  private readonly toast = inject(DsToastService);
  readonly sensitive = inject(SensitiveDataService);
  readonly id = input('');
  readonly client = computed(() => clients.find((c) => c.id === this.id()) ?? clients[0]);
  readonly products = products;
  readonly productKinds = productKinds;
  readonly actions: DsMenuItem[] = [
    { id: 'export', label: 'Exportar perfil', icon: 'arrow' },
    { id: 'revoke', label: 'Revocar consentimiento', icon: 'close', tone: 'danger' },
  ];

  onAction(id: string): void {
    this.toast.show(id === 'export' ? 'Exportación disponible próximamente.' : 'Revocación disponible próximamente.');
  }
  propose(product: string): void {
    this.toast.show(`Propuesta de ${product} preparada como borrador.`);
  }
}
