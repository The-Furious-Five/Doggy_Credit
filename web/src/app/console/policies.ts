import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DsAlert, DsBadge, DsButton, DsCard, DsIcon, DsSwitch, DsTable, DsTone, DsToastService } from '../design-system';

@Component({
  selector: 'dc-policies',
  imports: [FormsModule, DsAlert, DsBadge, DsButton, DsCard, DsIcon, DsSwitch, DsTable],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Políticas</h1>
        <p class="ds-muted">Reglas que convierten el análisis en decisiones automatizadas.</p>
      </div>
      <button dsButton (click)="toast.show('Editor de reglas disponible próximamente.')"><ds-icon name="plus" />Nueva regla</button>
    </header>
    <ds-alert heading="Los rechazos siempre pasan por una persona">
      Las reglas pueden preaprobar o derivar a revisión, pero nunca rechazan sin intervención humana.
    </ds-alert>
    <ds-card>
      <div class="ds-table-scroll" tabindex="0" aria-label="Reglas de decisión">
        <table dsTable>
          <caption class="ds-sr-only">Reglas de decisión configuradas</caption>
          <thead>
            <tr><th scope="col">Regla</th><th scope="col">Condición</th><th scope="col">Resultado</th><th scope="col">Estado</th></tr>
          </thead>
          <tbody>
            @for (rule of rules; track rule.name) {
              <tr>
                <th scope="row">{{ rule.name }}</th>
                <td class="ds-mono">{{ rule.condition }}</td>
                <td><ds-badge [tone]="rule.tone">{{ rule.result }}</ds-badge></td>
                <td><ds-switch [(ngModel)]="rule.active">{{ rule.active ? 'Activa' : 'Pausada' }}</ds-switch></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </ds-card>
  `,
})
export class Policies {
  readonly toast = inject(DsToastService);
  readonly rules: { name: string; condition: string; result: string; tone: DsTone; active: boolean }[] = [
    { name: 'Preaprobación microcrédito', condition: 'score ≥ 650 y endeudamiento < 40 %', result: 'Preaprobar', tone: 'success', active: true },
    { name: 'Derivar ecológico', condition: 'necesidad = eficiencia energética', result: 'Recomendar ecológico', tone: 'info', active: true },
    { name: 'Revisión PYME', condition: 'monto > Bs 200.000', result: 'Revisión manual', tone: 'warning', active: true },
    { name: 'Datos insuficientes', condition: 'fuentes completas < 2', result: 'Solicitar datos', tone: 'neutral', active: false },
  ];
}
