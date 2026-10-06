import { Component, booleanAttribute, inject, input, linkedSignal, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import {
  EvaluacionResumen,
  EvaluacionResumenDecisionEnum,
  EvaluacionResumenEstadoEnum,
  EvaluacionesService,
} from '../api';
import { DsAlert, DsBadge, DsButton, DsEmptyState, DsLink, DsPagination, DsSpinner, DsTable, DsTone } from '../design-system';
import { day } from './client-format';

const AMOUNT = new Intl.NumberFormat('es-BO', { maximumFractionDigits: 2 });

/** El estado es la decisión de la evaluación; nunca hay un rechazo automático. */
const STATUS: Record<EvaluacionResumenEstadoEnum, { label: string; tone: DsTone }> = {
  [EvaluacionResumenEstadoEnum.Preaprobado]: { label: 'Preaprobado', tone: 'success' },
  [EvaluacionResumenEstadoEnum.RevisionManual]: { label: 'Revisión manual', tone: 'info' },
  [EvaluacionResumenEstadoEnum.Parcial]: { label: 'Parcial', tone: 'warning' },
  [EvaluacionResumenEstadoEnum.Pendiente]: { label: 'Pendiente', tone: 'neutral' },
  [EvaluacionResumenEstadoEnum.Fallida]: { label: 'Fallida', tone: 'danger' },
};

/** Decisión de las políticas (C-05): se muestra en vez del estado cuando viene. */
const DECISION: Record<EvaluacionResumenDecisionEnum, { label: string; tone: DsTone }> = {
  [EvaluacionResumenDecisionEnum.Preaprobado]: { label: 'Preaprobado', tone: 'success' },
  [EvaluacionResumenDecisionEnum.RevisionManual]: { label: 'Revisión manual', tone: 'info' },
  [EvaluacionResumenDecisionEnum.SolicitarDatos]: { label: 'Solicitar datos', tone: 'neutral' },
};

/**
 * C-09: evaluaciones de GET /credit-assessments.
 * Con `applicantId` es el historial de un cliente (pestaña Score); sin él, las más
 * recientes del tenant (Resumen), con enlace al perfil de cada cliente.
 */
@Component({
  selector: 'dc-assessment-list',
  imports: [RouterLink, DsAlert, DsBadge, DsButton, DsEmptyState, DsLink, DsPagination, DsSpinner, DsTable],
  template: `
    @if (assessments.isLoading()) {
      <ds-spinner label="Cargando evaluaciones…" />
    } @else if (assessments.error()) {
      <ds-alert tone="danger" heading="No pudimos cargar las evaluaciones">
        <p>Revisa tu conexión y vuelve a intentarlo.</p>
        <button dsButton variant="secondary" (click)="assessments.reload()">Reintentar</button>
      </ds-alert>
    } @else if (assessments.value()?.items?.length) {
      <div class="ds-table-scroll" tabindex="0" [attr.aria-label]="label()">
        <table dsTable>
          <caption class="ds-sr-only">{{ label() }}</caption>
          <thead>
            <tr>
              <th scope="col">Fecha</th>
              @if (!applicantId()) {
                <th scope="col">Cliente</th>
              }
              <th scope="col" class="num">Score</th>
              <th scope="col">Banda</th>
              <th scope="col">Estado</th>
              @if (applicantId()) {
                <th scope="col" class="num">Monto</th>
                <th scope="col" class="num">Plazo</th>
              }
            </tr>
          </thead>
          <tbody>
            @for (item of assessments.value()!.items; track item.id) {
              <tr>
                <th scope="row">{{ day(item.creadoEn) }}</th>
                @if (!applicantId()) {
                  <td><a dsLink [routerLink]="['/app/clientes', item.solicitanteId]">Ver cliente</a></td>
                }
                <td class="num">{{ item.score }}</td>
                <td>{{ item.banda }}</td>
                <td><ds-badge [tone]="status(item).tone">{{ status(item).label }}</ds-badge></td>
                @if (applicantId()) {
                  <td class="num">{{ amount(item) }}</td>
                  <td class="num">{{ item.plazoMeses ? item.plazoMeses + ' m' : '—' }}</td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
      @if (paginate()) {
        <ds-pagination [label]="'Páginas de ' + label()" [total]="assessments.value()!.total" [pageSize]="pageSize()" [(page)]="page" />
      }
    } @else {
      <ds-empty-state
        heading="Sin evaluaciones"
        [description]="applicantId() ? 'Este cliente todavía no tiene evaluaciones.' : 'Todavía no hay evaluaciones. Empieza con una nueva evaluación.'"
        icon="tray"
      />
    }
  `,
})
export class AssessmentList {
  private readonly api = inject(EvaluacionesService);

  /** Solo las evaluaciones de este solicitante; vacío = todas las del tenant. */
  readonly applicantId = input('');
  readonly pageSize = input(10, { transform: numberAttribute });
  readonly paginate = input(false, { transform: booleanAttribute });
  readonly label = input('Evaluaciones');
  /** Vuelve a la página 1 si cambia el cliente. */
  readonly page = linkedSignal({ source: this.applicantId, computation: () => 1 });

  readonly assessments = rxResource({
    params: () => ({ applicantId: this.applicantId(), page: this.page(), size: this.pageSize() }),
    stream: ({ params }) =>
      this.api.listarEvaluaciones(params.applicantId || undefined, undefined, params.page, params.size),
  });

  readonly day = day;

  /** Un parcial se marca como tal (nunca se preaprueba); si no, la decisión, y si no hay, el estado. */
  status(item: EvaluacionResumen) {
    if (item.estado === EvaluacionResumenEstadoEnum.Parcial || !item.decision) return STATUS[item.estado];
    return DECISION[item.decision];
  }

  /** La evaluación guarda el monto sin moneda: se muestra el número, sin inventar el símbolo. */
  amount(item: EvaluacionResumen): string {
    return item.montoSolicitado ? AMOUNT.format(Number(item.montoSolicitado)) : '—';
  }
}
