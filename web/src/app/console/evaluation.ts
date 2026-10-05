import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsCheckbox,
  DsField,
  DsIcon,
  DsInput,
  DsLink,
  DsNumberField,
  DsRadio,
  DsSelect,
  DsStat,
  DsSteps,
} from '../design-system';
import { connectors, connectorStatus, products, purposeOptions } from '../data';

@Component({
  selector: 'dc-evaluation',
  imports: [
    FormsModule,
    RouterLink,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsCheckbox,
    DsField,
    DsIcon,
    DsInput,
    DsLink,
    DsNumberField,
    DsRadio,
    DsSelect,
    DsStat,
    DsSteps,
  ],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Nueva evaluación</h1>
        <p class="ds-muted">Consolida el perfil, calcula el score y recibe una recomendación.</p>
      </div>
    </header>
    <ds-steps label="Pasos de la evaluación" [steps]="steps" [current]="step()" />

    <ds-card>
      <form class="ds-stack form" (ngSubmit)="next()">
        @switch (step()) {
          @case (0) {
            <h2>Solicitante</h2>
            <fieldset class="ds-segmented">
              <legend class="ds-sr-only">Tipo de solicitante</legend>
              <label><input dsRadio type="radio" name="kind" value="persona" [(ngModel)]="kind" /><span>Persona</span></label>
              <label><input dsRadio type="radio" name="kind" value="empresa" [(ngModel)]="kind" /><span>Empresa</span></label>
            </fieldset>
            <div class="filters">
              <ds-field [label]="kind === 'empresa' ? 'Razón social' : 'Nombre completo'" required>
                <input dsInput name="name" [(ngModel)]="name" autocomplete="off" />
              </ds-field>
              <ds-field [label]="kind === 'empresa' ? 'NIT' : 'Documento de identidad'" required>
                <input dsInput name="document" [(ngModel)]="document" autocomplete="off" />
              </ds-field>
            </div>
            <div class="filters">
              <ds-field label="Monto solicitado">
                <ds-number-field name="amount" prefix="Bs" [min]="0" [step]="500" [(ngModel)]="amount" />
              </ds-field>
              <ds-field label="Plazo">
                <ds-number-field name="term" suffix="meses" [min]="1" [max]="84" [(ngModel)]="term" />
              </ds-field>
            </div>
            <ds-field label="Destino del crédito" hint="Ayuda a detectar la necesidad real.">
              <ds-select name="purpose" placeholder="Elige un destino" [options]="purposes" [(ngModel)]="purpose" />
            </ds-field>
          }
          @case (1) {
            <h2>Consentimiento</h2>
            <p class="ds-muted ds-reading">
              El cliente autoriza a tu institución a consultar sus datos financieros con la única
              finalidad de evaluar esta solicitud. La autorización vence en 90 días y puede revocarse
              en cualquier momento.
            </p>
            <div class="ds-stack-sm">
              <p class="ds-caption">Obligatorio</p>
              <label class="ds-choice"><input dsCheckbox type="checkbox" name="bureau" [(ngModel)]="consentBureau" />Consultar burós de crédito</label>
              <p class="ds-caption">Opcional</p>
              <label class="ds-choice"><input dsCheckbox type="checkbox" name="accounts" [(ngModel)]="consentAccounts" />Consultar cuentas y movimientos bancarios</label>
              <label class="ds-choice"><input dsCheckbox type="checkbox" name="wallet" [(ngModel)]="consentWallet" />Consultar billeteras digitales</label>
            </div>
            @if (missingConsent()) {
              <ds-alert tone="warning" heading="Falta el consentimiento obligatorio">
                Sin la consulta a burós no podemos calcular el score.
              </ds-alert>
            }
          }
          @case (2) {
            <h2>Fuentes</h2>
            <ul class="list">
              @for (connector of connectors; track connector.name) {
                <li>
                  <span class="ds-row"><span class="icon-tile"><ds-icon [name]="connector.icon" /></span>{{ connector.name }}</span>
                  <ds-badge [tone]="status[connector.status].tone">{{ status[connector.status].label }}</ds-badge>
                </li>
              }
            </ul>
            <p class="ds-caption">Solo se consultan las fuentes conectadas y autorizadas por el cliente.</p>
          }
          @case (3) {
            <div class="card-head"><h2>Resultado</h2><ds-badge tone="warning">Datos simulados</ds-badge></div>
            <div class="kpis">
              <ds-stat label="Score" value="702" detail="Riesgo bajo" />
              <ds-stat label="Capacidad de pago" value="Bs 3.100" detail="Cuota mensual máxima" />
              <ds-stat label="Necesidad" value="Eficiencia" detail="Energética" />
              <ds-stat label="Recomendación" [value]="products.eco.name" [detail]="products.eco.range" />
            </div>
            <ds-alert heading="Recomendación, no aprobación">
              La decisión final corresponde a tu política de crédito.
              <a dsLink routerLink="/app/clientes/c-1042">Ver perfil completo</a>
            </ds-alert>
          }
        }

        <div class="ds-actions">
          @if (step() > 0) {
            <button dsButton variant="secondary" (click)="step.set(step() - 1)">Atrás</button>
          }
          @if (step() < steps.length - 1) {
            <button dsButton type="submit">{{ step() === 2 ? 'Evaluar' : 'Continuar' }}</button>
          } @else {
            <button dsButton (click)="step.set(0)">Nueva evaluación</button>
          }
        </div>
      </form>
    </ds-card>
  `,
})
export class Evaluation {
  readonly steps = ['Solicitante', 'Consentimiento', 'Fuentes', 'Resultado'];
  readonly purposes = purposeOptions;
  readonly connectors = connectors;
  readonly status = connectorStatus;
  readonly products = products;
  readonly step = signal(0);
  readonly missingConsent = signal(false);
  kind = 'persona';
  name = '';
  document = '';
  amount: number | null = null;
  term: number | null = 12;
  purpose: string | null = null;
  // Nada preseleccionado: el cliente elige cada permiso.
  consentBureau = false;
  consentAccounts = false;
  consentWallet = false;

  next(): void {
    // ponytail: solo se valida el consentimiento obligatorio; el resto al conectar la API.
    const blocked = this.step() === 1 && !this.consentBureau;
    this.missingConsent.set(blocked);
    if (!blocked) this.step.set(Math.min(this.step() + 1, this.steps.length - 1));
  }
}
