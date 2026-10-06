import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DsCard, DsIcon, DsLink } from '../design-system';
import { AnalyticsDashboard } from './analytics-dashboard';
import { AssessmentList } from './assessment-list';

@Component({
  selector: 'dc-overview',
  imports: [RouterLink, DsCard, DsIcon, DsLink, AnalyticsDashboard, AssessmentList],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Resumen</h1>
        <p class="ds-muted">Evaluaciones, recomendaciones y necesidades de tu organización.</p>
      </div>
      <a dsLink appearance="primary" routerLink="/app/evaluaciones/nueva"
        ><ds-icon name="plus" />Nueva evaluación</a
      >
    </header>
    <dc-analytics-dashboard />
    <ds-card>
      <div class="card-head">
        <h2>Evaluaciones recientes</h2>
        <a dsLink routerLink="/app/clientes">Ver clientes <ds-icon name="arrow" /></a>
      </div>
      <dc-assessment-list label="Evaluaciones recientes" [pageSize]="5" />
    </ds-card>
  `,
})
export class Overview {}
