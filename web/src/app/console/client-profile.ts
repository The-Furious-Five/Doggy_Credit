import { Component, computed, inject, input, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  ConsentimientosService,
  PerfilFinanciero360,
  PerfilFinanciero360FuentesInner,
  SolicitantesService,
} from '../api';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsDialog,
  DsEmptyState,
  DsIcon,
  DsLink,
  DsMenu,
  DsMenuItem,
  DsSpinner,
  DsStat,
  DsTab,
  DsTable,
  DsTabs,
  DsToastService,
  DsTone,
} from '../design-system';
import { AssessmentList } from './assessment-list';
import { day, money, segmentLabel } from './client-format';
import { SensitiveDataService } from './sensitive-data.service';

type SourceState = PerfilFinanciero360FuentesInner['estado'];

const sourceInfo: Record<PerfilFinanciero360FuentesInner['fuente'], { name: string; data: string }> = {
  BURO: { name: 'Buró de crédito', data: 'Historial y deudas' },
  BANCO: { name: 'Core bancario', data: 'Cuentas y movimientos' },
  WALLET: { name: 'Billetera móvil', data: 'Flujos de ingresos' },
};

const stateBadge: Record<SourceState, { label: string; tone: DsTone }> = {
  COMPLETA: { label: 'Completa', tone: 'success' },
  SIN_REGISTRO: { label: 'Sin registro', tone: 'neutral' },
  NO_DISPONIBLE: { label: 'No disponible', tone: 'warning' },
  NO_CONSULTADA: { label: 'No consultada', tone: 'neutral' },
};

const incomeOrigin: Record<PerfilFinanciero360['origenIngreso'], string> = {
  BANCO: 'Promedio de 6 meses en el banco',
  WALLET: 'Promedio de 6 meses en la billetera',
  DECLARADO: 'Declarado por el cliente',
};

@Component({
  selector: 'dc-client-profile',
  imports: [
    AssessmentList,
    RouterLink,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsDialog,
    DsEmptyState,
    DsIcon,
    DsLink,
    DsMenu,
    DsSpinner,
    DsStat,
    DsTab,
    DsTable,
    DsTabs,
  ],
  styleUrl: './page.scss',
  template: `
    <a dsLink routerLink="/app/clientes"><ds-icon name="chevron-left" />Clientes</a>

    @if (client.isLoading()) {
      <ds-spinner label="Cargando cliente…" />
    } @else if (client.error()) {
      @if (notFound()) {
        <ds-empty-state heading="Cliente no encontrado" description="No existe o pertenece a otra institución.">
          <a dsLink appearance="secondary" routerLink="/app/clientes">Volver a clientes</a>
        </ds-empty-state>
      } @else {
        <ds-alert heading="No pudimos cargar el cliente" tone="danger">
          <p>Revisa tu conexión e inténtalo de nuevo.</p>
          <button dsButton variant="secondary" (click)="client.reload()">Reintentar</button>
        </ds-alert>
      }
    } @else if (client.value(); as c) {
      <header class="page-header">
        <div class="ds-stack-sm">
          <div class="ds-row">
            <ds-badge>{{ segmentLabel(c.segmento) }}</ds-badge>
            @if (consents.value()) {
              <ds-badge [tone]="activeConsents().length ? 'success' : 'warning'">
                {{ activeConsents().length ? 'Consentimiento vigente' : 'Sin consentimiento vigente' }}
              </ds-badge>
            }
          </div>
          <h1 class="ds-mono">{{ sensitive.document(c.documento) }}</h1>
          <p class="ds-muted">{{ c.actividad }} · Actualizado {{ day(c.perfil?.creadoEn ?? c.creadoEn) }}</p>
        </div>
        <div class="ds-row">
          <ds-menu label="Más acciones" icon="more" [items]="actions()" (itemSelected)="confirmRevoke.show()" />
          <a dsLink appearance="primary" routerLink="/app/evaluaciones/nueva">Reevaluar</a>
        </div>
      </header>

      <ds-tabs label="Perfil del cliente">
        <ds-tab value="resumen" label="Resumen">
          @if (c.perfil; as p) {
            <div class="kpis">
              <ds-card padding="compact"><ds-stat label="Ingreso mensual" [value]="sensitive.amount(money(p.ingresoMensual))" [detail]="origin(p)" /></ds-card>
              <ds-card padding="compact"><ds-stat label="Deuda vigente" [value]="sensitive.amount(money(p.deudaVigente))" [detail]="obligations(p)" /></ds-card>
              <ds-card padding="compact"><ds-stat label="Endeudamiento" [value]="p.endeudamientoPorcentaje ? p.endeudamientoPorcentaje + ' %' : '—'" detail="Cuota sobre ingreso" /></ds-card>
              <ds-card padding="compact"><ds-stat label="Capacidad de pago" [value]="sensitive.amount(money(p.capacidadPago))" detail="Mensual, después de egresos" /></ds-card>
              <ds-card padding="compact"><ds-stat label="Antigüedad" [value]="seniority(p)" detail="En el sistema financiero" /></ds-card>
            </div>
          } @else {
            <ds-empty-state heading="Sin perfil consolidado" description="El perfil se arma al evaluar al cliente con sus fuentes autorizadas.">
              <a dsLink appearance="secondary" routerLink="/app/evaluaciones/nueva">Evaluar ahora</a>
            </ds-empty-state>
          }
        </ds-tab>

        <ds-tab value="score" label="Score">
          <ds-card>
            <div class="card-head"><h2>Historial de evaluaciones</h2></div>
            <dc-assessment-list label="Historial de evaluaciones" [applicantId]="c.id" paginate />
          </ds-card>
        </ds-tab>

        <ds-tab value="recomendaciones" label="Recomendaciones">
          <ds-empty-state heading="Sin recomendaciones" description="Las recomendaciones de productos aparecerán aquí después de evaluar al cliente." />
        </ds-tab>

        <ds-tab value="fuentes" label="Fuentes">
          @if (c.perfil; as p) {
            <ds-card>
              <div class="ds-table-scroll" tabindex="0" aria-label="Fuentes de datos">
                <table dsTable>
                  <caption>Fuentes del último perfil, con la huella de lo recibido</caption>
                  <thead>
                    <tr><th scope="col">Fuente</th><th scope="col">Datos</th><th scope="col">Consultada</th><th scope="col">Huella</th><th scope="col">Estado</th></tr>
                  </thead>
                  <tbody>
                    @for (s of p.fuentes; track s.fuente) {
                      <tr>
                        <th scope="row">{{ sourceInfo[s.fuente].name }}</th>
                        <td>{{ sourceInfo[s.fuente].data }}</td>
                        <td>{{ day(s.consultadaEn) }}</td>
                        <td class="ds-mono">{{ s.sha256 ? s.sha256.slice(0, 12) : '—' }}</td>
                        <td><ds-badge [tone]="stateBadge[s.estado].tone">{{ stateBadge[s.estado].label }}</ds-badge></td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </ds-card>
          } @else {
            <ds-empty-state heading="Sin fuentes consultadas" description="Todavía no se consultó ninguna fuente para este cliente." />
          }
        </ds-tab>
      </ds-tabs>

      <ds-dialog #confirmRevoke heading="Revocar consentimiento">
        <div class="ds-stack">
          <p>
            Desde ahora no se consultará ninguna fuente de este cliente y sus evaluaciones nuevas serán
            rechazadas hasta que otorgue un consentimiento nuevo. El perfil ya consolidado se conserva.
          </p>
          <div class="ds-actions">
            <button dsButton variant="secondary" (click)="confirmRevoke.close()">Cancelar</button>
            <button dsButton variant="danger" [busy]="revoking()" (click)="revoke()">Revocar consentimiento</button>
          </div>
        </div>
      </ds-dialog>
    }
  `,
})
export class ClientProfile {
  private readonly applicants = inject(SolicitantesService);
  private readonly consentApi = inject(ConsentimientosService);
  private readonly toast = inject(DsToastService);
  readonly sensitive = inject(SensitiveDataService);
  readonly id = input('');
  readonly confirmDialog = viewChild(DsDialog);
  readonly segmentLabel = segmentLabel;
  readonly sourceInfo = sourceInfo;
  readonly stateBadge = stateBadge;
  readonly money = money;
  readonly day = day;
  readonly revoking = signal(false);

  readonly client = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.applicants.obtenerSolicitante(params),
  });
  readonly consents = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.consentApi.listarConsentimientos(params),
  });

  readonly activeConsents = computed(() => (this.consents.value() ?? []).filter((c) => c.estado === 'ACTIVO'));
  // «Exportar perfil» queda fuera de alcance (B-09).
  readonly actions = computed<DsMenuItem[]>(() => [
    { id: 'revoke', label: 'Revocar consentimiento', icon: 'close', tone: 'danger', disabled: !this.activeConsents().length },
  ]);
  readonly notFound = computed(() => (this.client.error() as HttpErrorResponse | undefined)?.status === 404);

  origin(p: PerfilFinanciero360): string {
    return incomeOrigin[p.origenIngreso];
  }

  obligations(p: PerfilFinanciero360): string {
    if (p.obligaciones == null) return 'Buró no disponible';
    return p.obligaciones === 1 ? '1 obligación' : `${p.obligaciones} obligaciones`;
  }

  seniority(p: PerfilFinanciero360): string {
    const months = p.antiguedadSistemaFinancieroMeses;
    if (months == null) return '—';
    return months < 12 ? `${months} meses` : `${Math.floor(months / 12)} años`;
  }

  revoke(): void {
    this.revoking.set(true);
    forkJoin(this.activeConsents().map((c) => this.consentApi.revocarConsentimiento(c.id!))).subscribe({
      next: () => {
        this.revoking.set(false);
        this.confirmDialog()?.close();
        this.consents.reload();
        this.toast.show('Consentimiento revocado. No se consultarán más fuentes de este cliente.');
      },
      error: () => {
        this.revoking.set(false);
        this.toast.show('No pudimos revocar el consentimiento. Sigue vigente; inténtalo de nuevo.');
      },
    });
  }
}
