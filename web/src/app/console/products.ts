import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DsBadge, DsCard, DsIcon, DsLink, DsSwitch } from '../design-system';
import { productKinds, products } from '../data';

@Component({
  selector: 'dc-products',
  imports: [FormsModule, RouterLink, DsBadge, DsCard, DsIcon, DsLink, DsSwitch],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Productos</h1>
        <p class="ds-muted">Líneas de crédito que DoggyCredit puede recomendar a tus clientes.</p>
      </div>
      <ds-badge tone="info">Productos propios en plan Pro</ds-badge>
    </header>
    <div class="ds-grid">
      @for (kind of productKinds; track kind) {
        <ds-card>
          <div class="ds-stack">
            <div class="ds-row ds-between">
              <span class="icon-tile"><ds-icon [name]="products[kind].icon" /></span>
              <ds-switch [(ngModel)]="active[kind]">Activo</ds-switch>
            </div>
            <div class="ds-stack-sm">
              <h2>{{ products[kind].name }}</h2>
              <p class="ds-muted">{{ products[kind].summary }}</p>
            </div>
            <dl class="facts">
              <div><dt class="ds-caption">Montos</dt><dd>{{ products[kind].range }}</dd></div>
              <div><dt class="ds-caption">Plazos</dt><dd>{{ products[kind].term }}</dd></div>
            </dl>
            <a dsLink routerLink="/app/politicas">Ver políticas <ds-icon name="arrow" /></a>
          </div>
        </ds-card>
      }
    </div>
  `,
})
export class Products {
  readonly products = products;
  readonly productKinds = productKinds;
  readonly active = { micro: true, eco: true, pyme: false };
}
