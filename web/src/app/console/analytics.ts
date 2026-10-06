import { Component } from '@angular/core';
import { AnalyticsDashboard } from './analytics-dashboard';

@Component({
  selector: 'dc-analytics',
  imports: [AnalyticsDashboard],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Analítica</h1>
        <p class="ds-muted">Indicadores del período y monitoreo de modelos de tu organización.</p>
      </div>
    </header>
    <dc-analytics-dashboard />
  `,
})
export class Analytics {}
