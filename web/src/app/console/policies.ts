import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Politica, PoliticaResultadoEnum, PoliticasService } from '../api';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsEmptyState,
  DsSkeleton,
  DsSwitch,
  DsTable,
  DsTone,
  DsToastService,
} from '../design-system';

/** Ninguna regla rechaza: solo preaprueba, deriva a revisión o pide datos. */
const RESULT: Record<PoliticaResultadoEnum, { label: string; tone: DsTone }> = {
  [PoliticaResultadoEnum.Preaprobado]: { label: 'Preaprobar', tone: 'success' },
  [PoliticaResultadoEnum.RevisionManual]: { label: 'Revisión manual', tone: 'warning' },
  [PoliticaResultadoEnum.SolicitarDatos]: { label: 'Solicitar datos', tone: 'neutral' },
};

type State = 'loading' | 'ready' | 'forbidden' | 'error';

@Component({
  selector: 'dc-policies',
  imports: [DsAlert, DsBadge, DsButton, DsCard, DsEmptyState, DsSkeleton, DsSwitch, DsTable],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Políticas</h1>
        <p class="ds-muted">Reglas que convierten el análisis en decisiones automatizadas.</p>
      </div>
    </header>
    <ds-alert heading="Los rechazos siempre pasan por una persona">
      Las reglas pueden preaprobar, derivar a revisión o pedir datos, pero nunca rechazan sin intervención
      humana. Un resultado parcial nunca se preaprueba.
    </ds-alert>

    @switch (state()) {
      @case ('loading') {
        <ds-card aria-busy="true" aria-label="Cargando reglas"><ds-skeleton /></ds-card>
      }
      @case ('forbidden') {
        <ds-alert tone="warning" heading="Solo el administrador del tenant puede ver las políticas">
          Pide a quien administra tu institución que revise las reglas.
        </ds-alert>
      }
      @case ('error') {
        <ds-alert tone="danger" heading="No pudimos cargar las políticas">
          <p>Revisa tu conexión y vuelve a intentarlo.</p>
          <button dsButton variant="secondary" (click)="load()">Reintentar</button>
        </ds-alert>
      }
      @case ('ready') {
        @if (rules().length === 0) {
          <ds-empty-state
            heading="Sin reglas"
            description="Tu institución todavía no tiene reglas de decisión. Toda evaluación queda en revisión manual."
            icon="tray"
          />
        } @else {
          <ds-card>
            <div class="ds-table-scroll" tabindex="0" aria-label="Reglas de decisión">
              <table dsTable>
                <caption class="ds-sr-only">Reglas de decisión configuradas</caption>
                <thead>
                  <tr><th scope="col">Regla</th><th scope="col">Condición</th><th scope="col">Resultado</th><th scope="col">Estado</th></tr>
                </thead>
                <tbody>
                  @for (rule of rules(); track rule.id) {
                    <tr>
                      <th scope="row">{{ rule.nombre }}</th>
                      <td class="ds-mono">{{ rule.condicion }}</td>
                      <td><ds-badge [tone]="result(rule).tone">{{ result(rule).label }}</ds-badge></td>
                      <td>
                        <ds-switch
                          [checked]="rule.activa"
                          [disabled]="saving() === rule.id"
                          (checkedChange)="setActive(rule, $event)"
                          >{{ rule.activa ? 'Activa' : 'Pausada'
                          }}<span class="ds-sr-only">: {{ rule.nombre }}</span></ds-switch
                        >
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </ds-card>
        }
      }
    }
  `,
})
export class Policies {
  private readonly api = inject(PoliticasService);
  private readonly toast = inject(DsToastService);

  readonly state = signal<State>('loading');
  readonly rules = signal<Politica[]>([]);
  /** Id de la regla que se está guardando: su interruptor queda deshabilitado mientras tanto. */
  readonly saving = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.api.listarPoliticas().subscribe({
      next: (rules) => {
        this.rules.set(rules);
        this.state.set('ready');
      },
      error: (error: HttpErrorResponse) => this.state.set(error.status === 403 ? 'forbidden' : 'error'),
    });
  }

  setActive(rule: Politica, activa: boolean): void {
    this.saving.set(rule.id);
    // Actualización optimista: cambia ya y, si el servidor falla, vuelve atrás.
    this.replace({ ...rule, activa });
    this.api.actualizarPolitica(rule.id, { activa }).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.saving.set(null);
        this.toast.show(`«${updated.nombre}» ${updated.activa ? 'activada' : 'pausada'}.`);
      },
      error: () => {
        this.replace(rule);
        this.saving.set(null);
        this.toast.show('No pudimos guardar el cambio. Vuelve a intentarlo.');
      },
    });
  }

  result(rule: Politica) {
    return RESULT[rule.resultado];
  }

  private replace(rule: Politica): void {
    this.rules.update((all) => all.map((r) => (r.id === rule.id ? rule : r)));
  }
}
