import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule, NgForm } from '@angular/forms';
import {
  AnalyticsModeloEstadoEnum,
  AnalyticsNecesidadCategoriaEnum,
  AnalyticsProductoTipoEnum,
  AnalyticsService,
} from '../api';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsDatePicker,
  DsEmptyState,
  DsField,
  DsProgress,
  DsSpinner,
  DsStat,
  DsTable,
  DsTone,
} from '../design-system';
import { money } from './client-format';
import { SensitiveDataService } from './sensitive-data.service';

const numberFormat = new Intl.NumberFormat('es-BO', { maximumFractionDigits: 2 });
const dates = new Intl.DateTimeFormat('es-BO', { timeZone: 'America/La_Paz' });
const needLabels: Record<AnalyticsNecesidadCategoriaEnum, string> = {
  CAPITAL_TRABAJO: 'Capital de trabajo',
  INVERSION_PRODUCTIVA: 'Inversión productiva',
  EFICIENCIA_ENERGETICA: 'Eficiencia energética',
  CONSUMO_CONSOLIDACION: 'Consumo y consolidación',
};
const productLabels: Record<AnalyticsProductoTipoEnum, string> = {
  MICROCREDITO: 'Microcrédito',
  VERDE: 'Crédito ecológico',
  PYME: 'Crédito PYME',
};
const modelStates: Record<AnalyticsModeloEstadoEnum, { label: string; tone: DsTone }> = {
  ESTABLE: { label: 'Estable', tone: 'success' },
  VIGILAR: { label: 'Vigilar', tone: 'warning' },
  ALERTA: { label: 'Alerta', tone: 'danger' },
  INSUFICIENTE: { label: 'Muestras insuficientes', tone: 'neutral' },
};

/** Una fuente de datos y estados para Resumen y Analítica, sin cifras de ejemplo. */
@Component({
  selector: 'dc-analytics-dashboard',
  imports: [
    FormsModule,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsDatePicker,
    DsEmptyState,
    DsField,
    DsProgress,
    DsSpinner,
    DsStat,
    DsTable,
  ],
  styleUrl: './page.scss',
  template: `
    <ds-card>
      <form class="filters" #filters="ngForm" (ngSubmit)="apply(filters)">
        <ds-field label="Desde" required>
          <ds-date-picker name="desde" [(ngModel)]="desdeDraft" required />
        </ds-field>
        <ds-field label="Hasta" required [error]="rangeError()">
          <ds-date-picker name="hasta" [(ngModel)]="hastaDraft" required />
        </ds-field>
        <div class="ds-row">
          <button
            dsButton
            type="submit"
            variant="secondary"
            [disabled]="filters.invalid || summary.isLoading()"
          >
            Aplicar período
          </button>
          <button
            dsButton
            variant="quiet"
            [disabled]="summary.isLoading()"
            (click)="summary.reload()"
          >
            Actualizar
          </button>
        </div>
      </form>
      <p class="ds-caption">Fechas inclusivas, hora de Bolivia. Integraciones: estado actual.</p>
    </ds-card>

    @if (summary.isLoading()) {
      <ds-spinner label="Cargando indicadores…" />
    } @else if (summary.error()) {
      <ds-alert heading="No pudimos cargar los indicadores" tone="danger">
        <p>Revisa tu conexión y vuelve a intentarlo. El período se conserva.</p>
        <button dsButton variant="secondary" (click)="summary.reload()">Reintentar</button>
      </ds-alert>
    } @else if (summary.value(); as data) {
      <div class="ds-row">
        <ds-badge>{{ planLabel(data.plan) }}</ds-badge>
        <span class="ds-caption">{{ date(data.desde) }} – {{ date(data.hasta) }}</span>
      </div>
      <div class="kpis">
        <ds-card padding="compact"
          ><ds-stat
            label="Evaluaciones"
            [value]="number(data.evaluaciones)"
            detail="En el período seleccionado"
        /></ds-card>
        <ds-card padding="compact"
          ><ds-stat
            label="Score promedio"
            [value]="number(data.scorePromedio)"
            detail="Escala 0 – 1000"
        /></ds-card>
        <ds-card padding="compact"
          ><ds-stat
            label="Con recomendación"
            [value]="percent(data.porcentajeConRecomendacion)"
            detail="Del total de evaluaciones"
        /></ds-card>
        <ds-card padding="compact"
          ><ds-stat
            label="Integraciones activas"
            [value]="number(data.integracionesActivas)"
            detail="Incluye el buró obligatorio"
        /></ds-card>
      </div>
      @if (data.evaluaciones === 0) {
        <ds-empty-state
          heading="Sin evaluaciones en este período"
          description="Selecciona otras fechas o registra una nueva evaluación para ver resultados."
        />
      }
      <ds-card>
        <div class="card-head"><h2>Necesidades detectadas</h2></div>
        <div class="bars">
          @for (need of data.necesidades; track need.categoria) {
            <ds-progress
              [label]="needLabels[need.categoria] + ' · ' + number(need.cantidad)"
              [value]="need.porcentaje"
            />
          }
        </div>
        <p class="ds-caption">
          Porcentaje del total evaluado. Los históricos sin necesidad registrada quedan sin
          categoría.
        </p>
      </ds-card>

      @if (data.analiticaAvanzada && data.pro; as pro) {
        <div class="kpis">
          <ds-card padding="compact"
            ><ds-stat
              label="Conversión"
              [value]="percent(pro.conversionPorcentaje)"
              detail="Preaprobadas sobre el total evaluado"
          /></ds-card>
          <ds-card padding="compact"
            ><ds-stat
              label="Monto recomendado"
              [value]="amount(pro.montoRecomendado, data.moneda)"
              detail="Primer producto recomendado por evaluación"
          /></ds-card>
          <ds-card padding="compact"
            ><ds-stat
              label="Tiempo de decisión"
              [value]="duration(pro.tiempoDecisionMedianaMs)"
              detail="Mediana del procesamiento registrado"
          /></ds-card>
        </div>
        <ds-card>
          <div class="card-head"><h2>Recomendaciones por producto</h2></div>
          @if (pro.recomendacionesPorProducto.length) {
            <div class="bars">
              @for (product of pro.recomendacionesPorProducto; track product.tipo) {
                <ds-progress
                  [label]="productLabels[product.tipo] + ' · ' + number(product.cantidad)"
                  [value]="product.porcentaje"
                />
                <p class="ds-caption">{{ amount(product.montoRecomendado, data.moneda) }}</p>
              }
            </div>
          } @else {
            <ds-empty-state
              heading="Sin recomendaciones"
              description="No hay productos recomendados registrados en este período."
            />
          }
        </ds-card>
        <ds-card>
          <div class="card-head">
            <h2>Modelos</h2>
            <span class="ds-caption">Último monitoreo semanal · D-08</span>
          </div>
          @if (pro.modelos.length) {
            <div class="ds-table-scroll" tabindex="0" aria-label="Monitoreo de modelos de scoring">
              <table dsTable>
                <caption class="ds-sr-only">
                  Deriva del score por modelo y ventana semanal
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Modelo</th>
                    <th scope="col" class="num">PSI</th>
                    <th scope="col">Ventana</th>
                    <th scope="col" class="num">Muestras / base</th>
                    <th scope="col">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  @for (model of pro.modelos; track model.version) {
                    <tr>
                      <th scope="row" class="ds-mono">{{ model.version }}</th>
                      <td class="num">{{ number(model.psi) }}</td>
                      <td>{{ date(model.desde) }} – {{ date(model.hasta) }}</td>
                      <td class="num">
                        {{ number(model.muestrasActuales) }} / {{ number(model.muestrasBase) }}
                      </td>
                      <td>
                        <ds-badge [tone]="modelStates[model.estado].tone">{{
                          modelStates[model.estado].label
                        }}</ds-badge>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <ds-empty-state
              heading="Sin monitoreo de modelos"
              description="El monitoreo semanal aparecerá después de que se ejecute el análisis de deriva."
            />
          }
        </ds-card>
      } @else {
        <ds-alert heading="Analítica avanzada disponible con Pro">
          Conversión, tiempo de decisión, montos, recomendaciones por producto y monitoreo de
          modelos requieren un plan con analítica avanzada.
        </ds-alert>
      }
    }
  `,
})
export class AnalyticsDashboard {
  private readonly api = inject(AnalyticsService);
  private readonly sensitive = inject(SensitiveDataService);
  private readonly today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/La_Paz' }).format(
    new Date(),
  );
  desdeDraft: string | null = this.today.slice(0, 8) + '01';
  hastaDraft: string | null = this.today;
  readonly rangeError = signal('');
  private readonly range = signal({ desde: this.desdeDraft!, hasta: this.hastaDraft! });
  readonly summary = rxResource({
    params: () => this.range(),
    stream: ({ params }) => this.api.obtenerResumenAnalytics(params.desde, params.hasta),
  });
  readonly needLabels = needLabels;
  readonly productLabels = productLabels;
  readonly modelStates = modelStates;

  apply(form: NgForm): void {
    if (form.invalid || !this.desdeDraft || !this.hastaDraft) return;
    if (this.desdeDraft > this.hastaDraft) {
      this.rangeError.set('Desde debe ser anterior o igual a hasta.');
      return;
    }
    this.rangeError.set('');
    this.range.set({ desde: this.desdeDraft, hasta: this.hastaDraft });
  }

  number(value?: number | null): string {
    return value == null ? '—' : numberFormat.format(value);
  }
  percent(value: number): string {
    return this.number(value) + ' %';
  }
  duration(ms?: number | null): string {
    return ms == null ? '—' : this.number(ms / 1000) + ' s';
  }
  amount(value: string, currency: string): string {
    return this.sensitive.amount(money({ amount: value, currency }));
  }
  date(iso: string): string {
    return dates.format(new Date(iso.length === 10 ? iso + 'T12:00:00-04:00' : iso));
  }
  planLabel(plan: string): string {
    return 'Plan ' + ({ FREE: 'Gratis', PRO: 'Pro', ENTERPRISE: 'Enterprise' }[plan] ?? plan);
  }
}
