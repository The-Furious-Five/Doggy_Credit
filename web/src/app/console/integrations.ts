import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import {
  FuenteIntegracion,
  Integracion,
  IntegracionEstadoEnum,
  IntegracionesService,
  ProveedorCredenciales,
} from '../api';
import { AuthSessionService } from '../auth/session.service';
import { IntegrationCredentials } from './integration-credentials';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsEmptyState,
  DsField,
  DsIcon,
  DsIconName,
  DsOption,
  DsSelect,
  DsSpinner,
  DsToastService,
  DsTone,
} from '../design-system';

type Category = 'Banco' | 'Buró' | 'Wallet' | 'Contabilidad' | 'Open banking';

/** Textos de presentación; el catálogo y sus estados pertenecen a la API. */
const PRESENTATION: Record<
  FuenteIntegracion,
  { name: string; category: Category; icon: DsIconName; description: string }
> = {
  [FuenteIntegracion.Buro]: {
    name: 'Buró de crédito nacional',
    category: 'Buró',
    icon: 'database',
    description: 'Historial, deudas vigentes y comportamiento de pago.',
  },
  [FuenteIntegracion.Banco]: {
    name: 'Core bancario',
    category: 'Banco',
    icon: 'building',
    description: 'Cuentas, saldos y movimientos de tus propios clientes.',
  },
  [FuenteIntegracion.Wallet]: {
    name: 'Billetera móvil',
    category: 'Wallet',
    icon: 'wallet',
    description: 'Flujos de ingresos y pagos frecuentes de la billetera.',
  },
  [FuenteIntegracion.OpenBanking]: {
    name: 'Agregador open banking',
    category: 'Open banking',
    icon: 'plug',
    description: 'Datos de otras entidades con consentimiento del cliente.',
  },
  [FuenteIntegracion.Facturacion]: {
    name: 'Facturación electrónica',
    category: 'Contabilidad',
    icon: 'store',
    description: 'Ventas declaradas y estacionalidad de la empresa.',
  },
  [FuenteIntegracion.Garantias]: {
    name: 'Registro de garantías',
    category: 'Buró',
    icon: 'shield',
    description: 'Garantías registradas y gravámenes vigentes.',
  },
};

const STATUS: Record<IntegracionEstadoEnum, { label: string; tone: DsTone }> = {
  [IntegracionEstadoEnum.Conectado]: { label: 'Conectado', tone: 'success' },
  [IntegracionEstadoEnum.Disponible]: { label: 'Disponible', tone: 'neutral' },
  [IntegracionEstadoEnum.RequierePro]: { label: 'Plan Pro', tone: 'info' },
  [IntegracionEstadoEnum.Proximamente]: { label: 'Próximamente', tone: 'neutral' },
};

@Component({
  selector: 'dc-integrations',
  imports: [
    FormsModule,
    IntegrationCredentials,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsEmptyState,
    DsField,
    DsIcon,
    DsSelect,
    DsSpinner,
  ],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Integraciones</h1>
        <p class="ds-muted">
          Conecta las fuentes que alimentan el perfil financiero de tus clientes.
        </p>
      </div>
      <ds-field label="Categoría">
        <ds-select
          [options]="categories"
          [ngModel]="category()"
          (ngModelChange)="category.set($event)"
        />
      </ds-field>
    </header>

    @switch (saveProblem()) {
      @case ('plan-limit') {
        <ds-alert tone="warning" heading="Tu plan Gratis incluye 1 integración" urgent>
          El buró obligatorio ya ocupa ese lugar. Mejora a Pro para sumar más fuentes.
        </ds-alert>
      }
      @case ('error') {
        <ds-alert tone="danger" heading="No pudimos guardar el cambio" urgent>
          La configuración se conserva. Revisa tu conexión y vuelve a intentar con el botón del
          conector.
        </ds-alert>
      }
      @case ('refresh') {
        <ds-alert
          tone="warning"
          heading="El cambio se guardó, pero no pudimos actualizar el catálogo"
          urgent
        >
          <p>Actualiza los estados antes de cambiar otra integración.</p>
          <button dsButton variant="secondary" (click)="load()">Actualizar estados</button>
        </ds-alert>
      }
    }

    @switch (state()) {
      @case ('loading') {
        <ds-spinner label="Cargando integraciones…" />
      }
      @case ('error') {
        <ds-alert tone="danger" heading="No pudimos cargar las integraciones">
          <p>Revisa tu conexión y vuelve a intentarlo. La categoría seleccionada se conserva.</p>
          <button dsButton variant="secondary" (click)="load()">Reintentar</button>
        </ds-alert>
      }
      @case ('ready') {
        @if (connectors().length === 0) {
          <ds-empty-state
            heading="Sin integraciones"
            description="No hay conectores disponibles para tu institución."
            icon="plug"
          />
        } @else if (visible().length === 0) {
          <ds-empty-state
            heading="Sin integraciones en esta categoría"
            description="Selecciona otra categoría para ver las fuentes disponibles."
            icon="plug"
          >
            <button dsButton variant="secondary" (click)="category.set('')">Ver todas</button>
          </ds-empty-state>
        } @else {
          <div class="ds-grid">
            @for (connector of visible(); track connector.fuente) {
              <ds-card>
                <div class="ds-stack">
                  <div class="ds-row ds-between">
                    <span class="icon-tile"><ds-icon [name]="presentation(connector).icon" /></span>
                    <ds-badge [tone]="status[connector.estado].tone">{{
                      status[connector.estado].label
                    }}</ds-badge>
                  </div>
                  <div class="ds-stack-sm">
                    <p class="ds-caption">{{ presentation(connector).category }}</p>
                    <h2>{{ presentation(connector).name }}</h2>
                    <p class="ds-muted">{{ presentation(connector).description }}</p>
                    @if (connector.fuente === mandatorySource) {
                      <p class="ds-caption">Esta fuente es obligatoria y permanece conectada.</p>
                    }
                  </div>
                  <button
                    dsButton
                    [variant]="connector.estado === connectedStatus ? 'secondary' : 'primary'"
                    [busy]="saving() === connector.fuente"
                    [disabled]="
                      saving() !== null || saveProblem() === 'refresh' || unavailable(connector)
                    "
                    [attr.aria-label]="actionLabel(connector) + ': ' + presentation(connector).name"
                    (click)="act(connector)"
                  >
                    {{ actionLabel(connector) }}
                  </button>
                  @if (canManageCredentials() && credentialSource(connector); as source) {
                    <dc-integration-credentials
                      [source]="source"
                      [name]="presentation(connector).name"
                    />
                  }
                </div>
              </ds-card>
            }
          </div>
        }
      }
    }
  `,
})
export class Integrations {
  private readonly api = inject(IntegracionesService);
  private readonly toast = inject(DsToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly session = inject(AuthSessionService);
  readonly canManageCredentials = computed(() =>
    this.session.session().roles.includes('TENANT_ADMIN'),
  );

  credentialSource(connector: Integracion): ProveedorCredenciales | null {
    switch (connector.fuente) {
      case FuenteIntegracion.Buro:
        return ProveedorCredenciales.Buro;
      case FuenteIntegracion.Banco:
        return ProveedorCredenciales.Banco;
      case FuenteIntegracion.Wallet:
        return ProveedorCredenciales.Wallet;
      default:
        return null;
    }
  }

  readonly status = STATUS;
  readonly mandatorySource = FuenteIntegracion.Buro;
  readonly connectedStatus = IntegracionEstadoEnum.Conectado;
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly connectors = signal<Integracion[]>([]);
  readonly saving = signal<FuenteIntegracion | null>(null);
  readonly saveProblem = signal<'plan-limit' | 'error' | 'refresh' | null>(null);
  readonly category = signal('');
  readonly categories: DsOption<string>[] = [
    { value: '', label: 'Todas' },
    ...[...new Set(Object.values(PRESENTATION).map((item) => item.category))].map((value) => ({
      value,
      label: value,
    })),
  ];
  readonly visible = computed(() =>
    this.connectors().filter(
      (connector) => !this.category() || this.presentation(connector).category === this.category(),
    ),
  );

  constructor() {
    this.load();
  }

  load(): void {
    if (this.saving()) return;
    this.state.set('loading');
    this.saveProblem.set(null);
    this.fetch();
  }

  act(connector: Integracion): void {
    if (
      this.state() !== 'ready' ||
      this.saving() ||
      this.saveProblem() === 'refresh' ||
      this.unavailable(connector)
    )
      return;
    this.saving.set(connector.fuente);
    this.saveProblem.set(null);
    this.api
      .actualizarIntegracion(connector.fuente, {
        activa: connector.estado !== IntegracionEstadoEnum.Conectado,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.connectors.update((all) =>
            all.map((item) => (item.fuente === updated.fuente ? updated : item)),
          );
          this.toast.show(`${this.presentation(updated).name}: ${STATUS[updated.estado].label}.`);
          // Activar/desactivar también puede cambiar REQUIERE_PRO en otros conectores.
          this.fetch(true);
        },
        error: (error: unknown) => {
          this.saving.set(null);
          this.saveProblem.set(
            error instanceof HttpErrorResponse && error.error?.code === 'PLAN_LIMIT_REACHED'
              ? 'plan-limit'
              : 'error',
          );
        },
      });
  }

  presentation(connector: Integracion) {
    return PRESENTATION[connector.fuente];
  }

  unavailable(connector: Integracion): boolean {
    return (
      connector.fuente === FuenteIntegracion.Buro ||
      connector.estado === IntegracionEstadoEnum.Proximamente
    );
  }

  actionLabel(connector: Integracion): string {
    if (connector.fuente === FuenteIntegracion.Buro) return 'Obligatorio';
    if (connector.estado === IntegracionEstadoEnum.Proximamente) return 'Próximamente';
    return connector.estado === IntegracionEstadoEnum.Conectado ? 'Desconectar' : 'Conectar';
  }

  private fetch(afterSave = false): void {
    this.api
      .listarIntegraciones()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (connectors) => {
          this.connectors.set(connectors);
          this.state.set('ready');
          this.saving.set(null);
        },
        error: () => {
          this.saving.set(null);
          if (afterSave) this.saveProblem.set('refresh');
          else this.state.set('error');
        },
      });
  }
}
