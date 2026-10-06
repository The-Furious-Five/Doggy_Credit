import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AuditoriaService } from '../api';
import { AuthSessionService } from '../auth/session.service';
import { DsAlert, DsButton, DsDatePicker, DsEmptyState, DsField, DsSpinner, DsTable } from '../design-system';

const PAGE_SIZE = 20;
const dateTime = new Intl.DateTimeFormat('es-BO', { dateStyle: 'short', timeStyle: 'medium' });

/** Medianoche local de una fecha ISO (aaaa-mm-dd), como instante ISO para la API. */
const startOfDay = (iso: string, plusDays = 0): string => {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day + plusDays).toISOString();
};

/**
 * C-08: bitácora de auditoría del tenant (GET /auditoria de C-04), solo para TENANT_ADMIN.
 * La API pagina por cursor (sin total), así que se navega con «más recientes / más antiguos»
 * guardando el cursor de cada página visitada.
 */
@Component({
  selector: 'dc-audit-log',
  imports: [FormsModule, DsAlert, DsButton, DsDatePicker, DsEmptyState, DsField, DsSpinner, DsTable],
  styleUrl: './page.scss',
  template: `
    @if (isAdmin()) {
      <section class="ds-stack" aria-labelledby="audit-title">
        <h2 id="audit-title">Bitácora de auditoría</h2>
        <form class="filters" (ngSubmit)="applyFilter()">
          <ds-field label="Desde">
            <ds-date-picker name="from" [max]="toDraft" [(ngModel)]="fromDraft" />
          </ds-field>
          <ds-field label="Hasta">
            <ds-date-picker name="to" [min]="fromDraft" [(ngModel)]="toDraft" />
          </ds-field>
          <div class="ds-row">
            <button dsButton type="submit" variant="secondary">Filtrar</button>
            @if (filtered()) {
              <button dsButton type="button" variant="quiet" (click)="clearFilter()">Limpiar</button>
            }
          </div>
        </form>

        @if (log.isLoading()) {
          <ds-spinner label="Cargando bitácora…" />
        } @else if (forbidden()) {
          <ds-alert tone="warning" heading="Solo el administrador del tenant puede ver la bitácora">
            Tu sesión no tiene el rol TENANT_ADMIN.
          </ds-alert>
        } @else if (log.error()) {
          <ds-alert tone="danger" heading="No pudimos cargar la bitácora">
            <p>Los filtros se conservan. Revisa tu conexión e inténtalo de nuevo.</p>
            <button dsButton variant="secondary" (click)="log.reload()">Reintentar</button>
          </ds-alert>
        } @else if (log.value()?.items?.length) {
          <div class="ds-table-scroll" tabindex="0" aria-label="Registros de auditoría">
            <table dsTable>
              <caption class="ds-sr-only">Registros de auditoría, del más reciente al más antiguo</caption>
              <thead>
                <tr>
                  <th scope="col">Fecha</th>
                  <th scope="col">Usuario</th>
                  <th scope="col">Acción</th>
                  <th scope="col">Recurso</th>
                  <th scope="col">Correlation ID</th>
                </tr>
              </thead>
              <tbody>
                @for (entry of log.value()!.items; track entry.id) {
                  <tr>
                    <td>{{ when(entry.creadoEn) }}</td>
                    <td>{{ entry.actor }}</td>
                    <th scope="row" class="ds-mono">{{ entry.accion }}</th>
                    <td>{{ entry.tipoRecurso }}</td>
                    <td class="ds-mono">{{ entry.correlationId }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="ds-row ds-between">
            <button dsButton variant="quiet" [disabled]="cursors().length === 0" (click)="newer()">Más recientes</button>
            <button dsButton variant="quiet" [disabled]="!log.value()?.siguienteCursor" (click)="older()">Más antiguos</button>
          </div>
        } @else {
          <ds-empty-state
            heading="Sin registros"
            [description]="filtered() ? 'No hay registros en esas fechas. Prueba con otro rango.' : 'Todavía no hay actividad registrada.'"
            icon="tray"
          />
        }
      </section>
    }
  `,
})
export class AuditLog {
  private readonly api = inject(AuditoriaService);
  private readonly session = inject(AuthSessionService).session;
  /**
   * Solo TENANT_ADMIN ve la bitácora; el backend lo exige igual (403 INSUFFICIENT_ROLE).
   * Los roles salen del access token (E-01), no de UserInfo, que no los trae.
   */
  readonly isAdmin = computed(() => this.session().roles.includes('TENANT_ADMIN'));

  fromDraft: string | null = null;
  toDraft: string | null = null;
  readonly from = signal<string | null>(null);
  readonly to = signal<string | null>(null);
  /** Cursores de las páginas anteriores a la actual (vacío = primera página). */
  readonly cursors = signal<(number | undefined)[]>([]);
  readonly cursor = signal<number | undefined>(undefined);

  readonly log = rxResource({
    params: () =>
      this.isAdmin() ? { from: this.from(), to: this.to(), cursor: this.cursor() } : undefined,
    stream: ({ params }) =>
      this.api.listarAuditoria(
        params.from ? startOfDay(params.from) : undefined,
        params.to ? startOfDay(params.to, 1) : undefined,
        params.cursor,
        PAGE_SIZE,
      ),
  });
  readonly forbidden = computed(() => (this.log.error() as HttpErrorResponse | undefined)?.status === 403);

  filtered(): boolean {
    return !!(this.from() || this.to());
  }

  applyFilter(): void {
    this.from.set(this.fromDraft);
    this.to.set(this.toDraft);
    this.restart();
  }

  clearFilter(): void {
    this.fromDraft = null;
    this.toDraft = null;
    this.applyFilter();
  }

  older(): void {
    const next = this.log.value()?.siguienteCursor;
    if (next === undefined) return;
    this.cursors.update((stack) => [...stack, this.cursor()]);
    this.cursor.set(next);
  }

  newer(): void {
    const stack = this.cursors();
    if (stack.length === 0) return;
    this.cursor.set(stack[stack.length - 1]);
    this.cursors.set(stack.slice(0, -1));
  }

  when(iso: string): string {
    return dateTime.format(new Date(iso));
  }

  private restart(): void {
    this.cursors.set([]);
    this.cursor.set(undefined);
  }
}
