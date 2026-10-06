import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Segmento, SolicitantesService } from '../api';
import {
  DsAlert,
  DsButton,
  DsCard,
  DsEmptyState,
  DsField,
  DsIcon,
  DsInput,
  DsLink,
  DsOption,
  DsPagination,
  DsSelect,
  DsSpinner,
  DsTable,
} from '../design-system';
import { day, money, segmentLabel, segmentLabels } from './client-format';
import { SensitiveDataService } from './sensitive-data.service';

const PAGE_SIZE = 10;

@Component({
  selector: 'dc-clients',
  imports: [
    FormsModule,
    RouterLink,
    DsAlert,
    DsButton,
    DsCard,
    DsEmptyState,
    DsField,
    DsIcon,
    DsInput,
    DsLink,
    DsPagination,
    DsSelect,
    DsSpinner,
    DsTable,
  ],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Clientes</h1>
        <p class="ds-muted">Solicitantes registrados y su perfil financiero consolidado.</p>
      </div>
      <a dsLink appearance="primary" routerLink="/app/evaluaciones/nueva"><ds-icon name="plus" />Nueva evaluación</a>
    </header>

    <ds-card>
      <div class="ds-stack">
        <form class="filters" (ngSubmit)="search()">
          <ds-field label="Documento" hint="Búsqueda exacta, con o sin puntos.">
            <input dsInput type="search" name="document" [(ngModel)]="documentDraft" />
          </ds-field>
          <ds-field label="Segmento">
            <ds-select name="segment" [options]="segments" [ngModel]="segment()" (ngModelChange)="setSegment($event)" />
          </ds-field>
          <div class="ds-row">
            <button dsButton type="submit" variant="secondary">Buscar</button>
          </div>
        </form>

        @if (clients.isLoading()) {
          <ds-spinner label="Cargando clientes…" />
        } @else if (clients.error()) {
          <ds-alert heading="No pudimos cargar los clientes" tone="danger">
            <p>Los filtros se conservan. Revisa tu conexión e inténtalo de nuevo.</p>
            <button dsButton variant="secondary" (click)="clients.reload()">Reintentar</button>
          </ds-alert>
        } @else if (clients.value()?.items?.length) {
          <div class="ds-table-scroll" tabindex="0" aria-label="Listado de clientes">
            <table dsTable>
              <caption class="ds-sr-only">Clientes registrados</caption>
              <thead>
                <tr>
                  <th scope="col">Documento</th>
                  <th scope="col">Segmento</th>
                  <th scope="col">Actividad</th>
                  <th scope="col">Destino del crédito</th>
                  <th scope="col" class="num">Ingreso declarado</th>
                  <th scope="col">Registrado</th>
                </tr>
              </thead>
              <tbody>
                @for (client of clients.value()!.items; track client.id) {
                  <tr>
                    <th scope="row"><a dsLink class="ds-mono" [routerLink]="client.id">{{ sensitive.document(client.documento) }}</a></th>
                    <td>{{ segmentLabel(client.segmento) }}</td>
                    <td>{{ client.actividad }}</td>
                    <td>{{ client.destinoCredito }}</td>
                    <td class="num">{{ sensitive.amount(money({ amount: client.ingresosMensuales, currency: client.moneda })) }}</td>
                    <td>{{ day(client.creadoEn) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <ds-pagination label="Páginas de clientes" [total]="clients.value()!.total" [pageSize]="pageSize" [(page)]="page" />
        } @else {
          <ds-empty-state heading="Sin clientes" [description]="emptyMessage()" icon="search">
            @if (filtered()) {
              <button dsButton variant="secondary" (click)="clear()">Limpiar filtros</button>
            }
          </ds-empty-state>
        }
      </div>
    </ds-card>
  `,
})
export class Clients {
  private readonly api = inject(SolicitantesService);
  readonly sensitive = inject(SensitiveDataService);
  readonly segmentLabel = segmentLabel;
  readonly money = money;
  readonly day = day;
  readonly pageSize = PAGE_SIZE;
  readonly segments: DsOption<Segmento | ''>[] = [
    { value: '', label: 'Todos los segmentos' },
    ...(Object.keys(segmentLabels) as Segmento[]).map((value) => ({ value, label: segmentLabels[value] })),
  ];

  documentDraft = '';
  readonly document = signal('');
  readonly segment = signal<Segmento | ''>('');
  readonly page = signal(1);

  // El documento viaja en la cabecera X-Documento, nunca en la URL (B-08).
  readonly clients = rxResource({
    params: () => ({ page: this.page(), segment: this.segment(), document: this.document() }),
    stream: ({ params }) =>
      this.api.listarSolicitantes(params.page, PAGE_SIZE, params.segment || undefined, params.document || undefined),
  });

  filtered(): boolean {
    return !!(this.document() || this.segment());
  }

  emptyMessage(): string {
    return this.filtered()
      ? 'Ningún cliente coincide con el documento o el segmento. Prueba con otros filtros.'
      : 'Todavía no hay clientes. Se registran al iniciar una nueva evaluación.';
  }

  search(): void {
    this.page.set(1);
    this.document.set(this.documentDraft.trim());
  }

  setSegment(segment: Segmento | ''): void {
    this.page.set(1);
    this.segment.set(segment);
  }

  clear(): void {
    this.documentDraft = '';
    this.page.set(1);
    this.document.set('');
    this.segment.set('');
  }
}
