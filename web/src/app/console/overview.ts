import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  DsAlert,
  DsCard,
  DsIcon,
  DsLink,
  DsProgress,
  DsStat,
} from '../design-system';
import { needs, productKinds, products } from '../data';
import { AssessmentList } from './assessment-list';

@Component({
  selector: 'dc-overview',
  imports: [RouterLink, AssessmentList, DsAlert, DsCard, DsIcon, DsLink, DsProgress, DsStat],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <p class="ds-eyebrow">Octubre 2026</p>
        <h1>Resumen</h1>
      </div>
      <a dsLink appearance="primary" routerLink="/app/evaluaciones/nueva"><ds-icon name="plus" />Nueva evaluación</a>
    </header>

    <div class="kpis">
      <ds-card padding="compact"><ds-stat label="Evaluaciones" value="1.284" detail="12 % más que septiembre" /></ds-card>
      <ds-card padding="compact"><ds-stat label="Score promedio" value="682" detail="Escala 300 – 850" /></ds-card>
      <ds-card padding="compact"><ds-stat label="Con recomendación" value="64 %" detail="Del total evaluado" /></ds-card>
      <ds-card padding="compact"><ds-stat label="Integraciones activas" value="1 de 1" detail="Límite del plan Gratis" /></ds-card>
    </div>

    <div class="split">
      <ds-card>
        <div class="card-head">
          <h2>Evaluaciones recientes</h2>
          <a dsLink routerLink="/app/clientes">Ver clientes <ds-icon name="arrow" /></a>
        </div>
        <dc-assessment-list label="Evaluaciones recientes" [pageSize]="5" />
      </ds-card>

      <div class="ds-stack">
        <ds-card>
          <div class="card-head"><h2>Necesidades detectadas</h2></div>
          <div class="bars">
            @for (need of needs; track need.label) {
              <ds-progress [label]="need.label" [value]="need.value" />
            }
          </div>
        </ds-card>
        <ds-card>
          <div class="card-head"><h2>Recomendaciones</h2></div>
          <ul class="list">
            @for (kind of productKinds; track kind) {
              <li>
                <span class="ds-row"><span class="icon-tile"><ds-icon [name]="products[kind].icon" /></span>{{ products[kind].name }}</span>
                <strong class="num">{{ recommended[kind] }}</strong>
              </li>
            }
          </ul>
        </ds-card>
      </div>
    </div>

    <ds-alert heading="Usaste el 68 % de tus consultas del mes">
      Al llegar a 500 pausaremos nuevas evaluaciones hasta noviembre.
      <a dsLink routerLink="/app/configuracion" fragment="plan">Conoce el plan Pro</a>
    </ds-alert>
  `,
})
export class Overview {
  readonly needs = needs;
  readonly products = products;
  readonly productKinds = productKinds;
  readonly recommended = { micro: 612, eco: 231, pyme: 441 };
}
