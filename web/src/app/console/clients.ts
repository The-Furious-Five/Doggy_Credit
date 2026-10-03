import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  DsBadge,
  DsButton,
  DsCard,
  DsEmptyState,
  DsField,
  DsIcon,
  DsInput,
  DsLink,
  DsPagination,
  DsSelect,
  DsTable,
} from '../design-system';
import { clients, products, segmentOptions } from '../data';

const normalize = (text: string) =>
  text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

@Component({
  selector: 'dc-clients',
  imports: [
    FormsModule,
    RouterLink,
    DsBadge,
    DsButton,
    DsCard,
    DsEmptyState,
    DsField,
    DsIcon,
    DsInput,
    DsLink,
    DsPagination,
    DsSelect,
    DsTable,
  ],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Clientes</h1>
        <p class="ds-muted">Perfiles financieros consolidados de las personas y empresas evaluadas.</p>
      </div>
      <a dsLink appearance="primary" routerLink="/app/evaluaciones/nueva"><ds-icon name="plus" />Nueva evaluación</a>
    </header>

    <ds-card>
      <div class="ds-stack">
        <div class="filters">
          <ds-field label="Buscar" hint="Nombre o necesidad">
            <input dsInput type="search" name="query" [ngModel]="query()" (ngModelChange)="query.set($event)" />
          </ds-field>
          <ds-field label="Segmento">
            <ds-select [options]="segments" [ngModel]="segment()" (ngModelChange)="segment.set($event)" />
          </ds-field>
        </div>

        @if (filtered().length) {
          <div class="ds-table-scroll" tabindex="0" aria-label="Listado de clientes">
            <table dsTable>
              <caption class="ds-sr-only">Clientes evaluados</caption>
              <thead>
                <tr>
                  <th scope="col">Cliente</th>
                  <th scope="col">Documento</th>
                  <th scope="col">Segmento</th>
                  <th scope="col" class="num">Score</th>
                  <th scope="col">Necesidad</th>
                  <th scope="col">Producto sugerido</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Actualizado</th>
                </tr>
              </thead>
              <tbody>
                @for (client of filtered(); track client.id) {
                  <tr>
                    <th scope="row"><a dsLink [routerLink]="client.id">{{ client.name }}</a></th>
                    <td class="ds-mono">{{ client.document }}</td>
                    <td>{{ client.segment }}</td>
                    <td class="num">{{ client.score }}</td>
                    <td>{{ client.need }}</td>
                    <td>{{ products[client.product].name }}</td>
                    <td><ds-badge [tone]="client.status.tone">{{ client.status.label }}</ds-badge></td>
                    <td>{{ client.updated }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <ds-pagination label="Páginas de clientes" [total]="filtered().length" [(page)]="page" />
        } @else {
          <ds-empty-state heading="Sin resultados" description="Ningún cliente coincide con la búsqueda. Prueba con otro término o segmento." icon="search">
            <button dsButton variant="secondary" (click)="clear()">Limpiar filtros</button>
          </ds-empty-state>
        }
      </div>
    </ds-card>
  `,
})
export class Clients {
  readonly products = products;
  readonly segments = segmentOptions;
  readonly query = signal('');
  readonly segment = signal('');
  readonly page = signal(1);
  readonly filtered = computed(() => {
    const query = normalize(this.query());
    return clients.filter(
      (c) =>
        (!this.segment() || c.segment === this.segment()) &&
        normalize(c.name + ' ' + c.need).includes(query),
    );
  });

  clear(): void {
    this.query.set('');
    this.segment.set('');
  }
}
