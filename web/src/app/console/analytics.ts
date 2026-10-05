import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DsAlert, DsBadge, DsCard, DsLink, DsProgress, DsStat, DsTable } from '../design-system';
import { needs } from '../data';

@Component({
  selector: 'dc-analytics',
  imports: [RouterLink, DsAlert, DsBadge, DsCard, DsLink, DsProgress, DsStat, DsTable],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Analítica</h1>
        <p class="ds-muted">Conversión, cartera recomendada y desempeño de los modelos.</p>
      </div>
      <ds-badge tone="info">Plan Pro</ds-badge>
    </header>
    <ds-alert heading="Vista previa del plan Pro">
      Con Pro, estos reportes se actualizan cada hora y se pueden exportar.
      <a dsLink routerLink="/app/configuracion" fragment="plan">Comparar planes</a>
    </ds-alert>

    <div class="kpis">
      <ds-card padding="compact"><ds-stat label="Conversión" value="38 %" detail="Recomendación a desembolso" /></ds-card>
      <ds-card padding="compact"><ds-stat label="Monto recomendado" value="Bs 8,2 M" detail="Últimos 30 días" /></ds-card>
      <ds-card padding="compact"><ds-stat label="Tiempo de decisión" value="4 min" detail="Mediana" /></ds-card>
      <ds-card padding="compact"><ds-stat label="Mora a 90 días" value="2,1 %" detail="Cartera recomendada" /></ds-card>
    </div>

    <div class="split">
      <ds-card>
        <div class="card-head"><h2>Recomendaciones por producto</h2></div>
        <div class="bars">
          <ds-progress label="Microcrédito" [value]="48" />
          <ds-progress label="Crédito PYME" [value]="34" />
          <ds-progress label="Crédito ecológico" [value]="18" />
        </div>
      </ds-card>
      <ds-card>
        <div class="card-head"><h2>Necesidades</h2></div>
        <div class="bars">
          @for (need of needs; track need.label) {
            <ds-progress [label]="need.label" [value]="need.value" />
          }
        </div>
      </ds-card>
    </div>

    <ds-card>
      <div class="card-head"><h2>Modelos</h2><span class="ds-caption">Versionados y monitoreados</span></div>
      <div class="ds-table-scroll" tabindex="0" aria-label="Modelos de scoring">
        <table dsTable>
          <caption class="ds-sr-only">Versiones de modelos de scoring</caption>
          <thead>
            <tr><th scope="col">Modelo</th><th scope="col" class="num">AUC</th><th scope="col">Desplegado</th><th scope="col">Estado</th></tr>
          </thead>
          <tbody>
            <tr><th scope="row" class="ds-mono">scoring-v2.3</th><td class="num">0,81</td><td>15/09/2026</td><td><ds-badge tone="success">En producción</ds-badge></td></tr>
            <tr><th scope="row" class="ds-mono">scoring-v2.4</th><td class="num">0,83</td><td>—</td><td><ds-badge tone="info">En evaluación</ds-badge></td></tr>
            <tr><th scope="row" class="ds-mono">necesidades-v1.1</th><td class="num">0,77</td><td>02/08/2026</td><td><ds-badge tone="success">En producción</ds-badge></td></tr>
          </tbody>
        </table>
      </div>
    </ds-card>
  `,
})
export class Analytics {
  readonly needs = needs;
}
