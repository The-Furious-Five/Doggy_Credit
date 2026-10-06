import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DsIcon, DsLink } from '../design-system';
import { AnalyticsDashboard } from './analytics-dashboard';

@Component({
  selector: 'dc-overview',
  imports: [RouterLink, DsIcon, DsLink, AnalyticsDashboard],
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
  `,
})
export class Overview {}
