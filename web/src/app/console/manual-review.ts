import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DOCUMENT } from '@angular/common';
import {
  CasoRevision,
  CasoRevisionMotivoEnum,
  Producto,
  ResolucionNuevaDecisionEnum,
  RevisionManualService,
} from '../api';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsDialog,
  DsEmptyState,
  DsField,
  DsInput,
  DsLink,
  DsOption,
  DsPagination,
  DsRadio,
  DsSelect,
  DsSpinner,
  DsTable,
  DsToastService,
  DsTone,
} from '../design-system';
import { HANDLED_PROBLEMS } from '../http/api-error.interceptor';
import { day, isMasked, money } from './client-format';
import { productNames } from './product-format';
import { SensitiveDataService } from './sensitive-data.service';

const PAGE_SIZE = 10;
/** La API exige al menos 10 caracteres (ResolucionNueva.justificacion). */
const MIN_JUSTIFICATION = 10;
const AMOUNT = new Intl.NumberFormat('es-BO', { maximumFractionDigits: 2 });

const MOTIVE: Record<CasoRevisionMotivoEnum, { label: string; tone: DsTone }> = {
  [CasoRevisionMotivoEnum.RevisionManual]: { label: 'Revisión manual', tone: 'info' },
  [CasoRevisionMotivoEnum.SolicitarDatos]: { label: 'Solicitar datos', tone: 'neutral' },
};

/** Errores del contrato que el diálogo explica por sí mismo (sin repetirlos en un toast). */
type SubmitProblem = 'invalid' | 'resolved' | 'product' | 'forbidden' | 'error' | null;

const code = (error: unknown): string =>
  error instanceof HttpErrorResponse && error.error && typeof error.error === 'object'
    ? String((error.error as Record<string, unknown>)['code'] ?? '')
    : '';

/**
 * Bandeja de revisión manual (E-10, RF-DEC-03): los casos que las políticas dejaron
 * para una persona y su resolución (override) con justificación obligatoria y, al
 * aprobar, el producto elegido. Contrato de D-10, backend de C-10.
 */
@Component({
  selector: 'dc-manual-review',
  imports: [
    FormsModule,
    RouterLink,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsDialog,
    DsEmptyState,
    DsField,
    DsInput,
    DsLink,
    DsPagination,
    DsRadio,
    DsSelect,
    DsSpinner,
    DsTable,
  ],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1 #heading tabindex="-1">Revisión manual</h1>
        <p class="ds-muted">
          Casos que tus políticas dejaron para una persona. Resuélvelos con una justificación.
        </p>
      </div>
    </header>

    <ds-card>
      @if (cases.isLoading() && !cases.hasValue()) {
        <ds-spinner label="Cargando casos…" />
      } @else if (cases.error()) {
        @if (status(cases.error()) === 403) {
          <ds-alert tone="warning" heading="Tu rol no resuelve casos en revisión manual">
            Pide a una persona analista o a quien administra tu institución que los revise.
          </ds-alert>
        } @else {
          <ds-alert tone="danger" heading="No pudimos cargar la bandeja">
            <p>Revisa tu conexión y vuelve a intentarlo.</p>
            <button dsButton variant="secondary" (click)="cases.reload()">Reintentar</button>
          </ds-alert>
        }
      } @else if (cases.value()?.items?.length) {
        <div class="ds-table-scroll" tabindex="0" aria-label="Casos en revisión manual">
          <table dsTable>
            <caption class="ds-sr-only">
              Casos en revisión manual, del más antiguo al más reciente
            </caption>
            <thead>
              <tr>
                <th scope="col">Cliente</th>
                <th scope="col">Recibido</th>
                <th scope="col" class="num">Score</th>
                <th scope="col">Motivo</th>
                <th scope="col" class="num">Monto</th>
                <th scope="col" class="num">Plazo</th>
                <th scope="col"><span class="ds-sr-only">Acciones</span></th>
              </tr>
            </thead>
            <tbody>
              @for (item of cases.value()!.items; track item.evaluacionId) {
                <tr>
                  <th scope="row">
                    <a
                      dsLink
                      class="ds-mono"
                      [routerLink]="['/app/clientes', item.solicitanteId]"
                      >{{ sensitive.document(item.documento) }}</a
                    >
                  </th>
                  <td>{{ day(item.creadoEn) }}</td>
                  <td class="num">{{ item.score }} · {{ item.banda }}</td>
                  <td>
                    <div class="ds-row">
                      <ds-badge [tone]="motive[item.motivo].tone">{{
                        motive[item.motivo].label
                      }}</ds-badge>
                      @if (item.resultadoParcial) {
                        <ds-badge tone="warning">Resultado parcial</ds-badge>
                      }
                    </div>
                  </td>
                  <td class="num">{{ amount(item) }}</td>
                  <td class="num">{{ item.plazoMeses ? item.plazoMeses + ' m' : '—' }}</td>
                  <td>
                    <button
                      dsButton
                      variant="secondary"
                      [attr.aria-label]="'Resolver caso de ' + sensitive.document(item.documento)"
                      (click)="open(item)"
                    >
                      Resolver
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <ds-pagination
          label="Páginas de la bandeja"
          [total]="cases.value()!.total"
          [pageSize]="pageSize"
          [(page)]="page"
        />
      } @else {
        <ds-empty-state
          heading="Sin casos pendientes"
          description="Cuando una política deje una evaluación en revisión manual, aparecerá aquí."
          icon="tray"
        />
      }
    </ds-card>

    <ds-dialog #dialog heading="Resolver caso" (dismissed)="closed()">
      @if (selected(); as item) {
        <form class="ds-stack" novalidate (ngSubmit)="submit(item)">
          <dl class="facts">
            <div>
              <dt class="ds-caption">Cliente</dt>
              <dd class="ds-mono">{{ sensitive.document(item.documento) }}</dd>
            </div>
            <div>
              <dt class="ds-caption">Score</dt>
              <dd>{{ item.score }} · Banda {{ item.banda }}</dd>
            </div>
            <div>
              <dt class="ds-caption">Solicitud</dt>
              <dd>{{ amount(item) }} · {{ item.plazoMeses ? item.plazoMeses + ' meses' : '—' }}</dd>
            </div>
          </dl>
          @if (item.proposito) {
            <p><span class="ds-caption">Destino declarado:</span> {{ item.proposito }}</p>
          }
          @if (item.resultadoParcial) {
            <ds-alert tone="warning" heading="Resultado parcial">
              Una fuente no respondió durante la evaluación; el score se calculó sin ella.
            </ds-alert>
          }
          @if (item.razones.length) {
            <div class="ds-stack-sm">
              <h3>Principales razones</h3>
              <ul class="list">
                @for (reason of item.razones.slice(0, 3); track reason) {
                  <li class="ds-mono">{{ reason }}</li>
                }
              </ul>
            </div>
          }

          <fieldset
            class="ds-segmented"
            [attr.aria-describedby]="decisionError() ? 'revision-decision-error' : null"
          >
            <legend class="ds-sr-only">Decisión</legend>
            <label
              ><input
                #firstDecision
                dsRadio
                type="radio"
                name="decision"
                [value]="approve"
                [(ngModel)]="decision"
              /><span>Aprobar</span></label
            >
            <label
              ><input
                dsRadio
                type="radio"
                name="decision"
                [value]="reject"
                [(ngModel)]="decision"
              /><span>Rechazar</span></label
            >
          </fieldset>
          @if (decisionError()) {
            <ds-alert
              id="revision-decision-error"
              tone="warning"
              heading="Elige si apruebas o rechazas el caso"
            />
          }

          @if (decision === approve) {
            @if (productOptions().length) {
              <ds-field
                controlId="revision-producto"
                label="Producto que se otorga"
                required
                [error]="productError()"
              >
                <ds-select
                  name="producto"
                  placeholder="Elige un producto"
                  [options]="productOptions()"
                  [(ngModel)]="productId"
                />
              </ds-field>
            } @else {
              <ds-alert tone="warning" heading="No hay productos activos">
                Para aprobar hace falta al menos un producto activo en el catálogo. Puedes rechazar
                el caso o pedir a quien administra tu institución que active uno.
              </ds-alert>
            }
          }

          <ds-field
            controlId="revision-justificacion"
            label="Justificación"
            hint="Al menos 10 caracteres. Queda registrada en la auditoría con tu usuario."
            required
            [error]="justificationError()"
          >
            <textarea
              dsInput
              name="justificacion"
              rows="4"
              maxlength="1000"
              [(ngModel)]="justification"
            ></textarea>
          </ds-field>

          @switch (problem()) {
            @case ('invalid') {
              <ds-alert tone="warning" heading="La API rechazó la resolución" urgent>
                Revisa la decisión, el producto y la justificación. Tus datos se conservan.
              </ds-alert>
            }
            @case ('resolved') {
              <ds-alert tone="warning" heading="Este caso ya no está pendiente" urgent>
                Otra persona lo resolvió mientras lo revisabas. Vuelve a la bandeja para ver los
                casos que siguen pendientes.
              </ds-alert>
            }
            @case ('product') {
              <ds-alert tone="warning" heading="Ese producto ya no está disponible" urgent>
                Se desactivó mientras revisabas el caso. Elige otro; la lista ya se actualizó.
              </ds-alert>
            }
            @case ('forbidden') {
              <ds-alert tone="warning" heading="Tu rol no puede resolver casos" urgent>
                Pide a una persona analista o a quien administra tu institución que lo resuelva.
              </ds-alert>
            }
            @case ('error') {
              <ds-alert tone="danger" heading="No pudimos guardar la resolución" urgent>
                Tu decisión y tu justificación se conservan. Revisa tu conexión y vuelve a
                intentarlo.
              </ds-alert>
            }
          }

          <div class="ds-actions">
            @if (problem() === 'resolved') {
              <button dsButton type="button" (click)="backToQueue()">Volver a la bandeja</button>
            } @else {
              <button dsButton type="button" variant="secondary" (click)="dialog.close()">
                Cancelar
              </button>
              <button dsButton type="submit" [busy]="saving()">Resolver caso</button>
            }
          </div>
        </form>
      }
    </ds-dialog>
  `,
})
export class ManualReview {
  private readonly api = inject(RevisionManualService);
  private readonly toast = inject(DsToastService);
  private readonly document = inject(DOCUMENT);
  readonly sensitive = inject(SensitiveDataService);
  readonly dialog = viewChild.required<DsDialog>('dialog');
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  readonly approve = ResolucionNuevaDecisionEnum.Aprobado;
  readonly reject = ResolucionNuevaDecisionEnum.Rechazado;
  readonly motive = MOTIVE;
  readonly day = day;
  readonly pageSize = PAGE_SIZE;
  readonly page = signal(1);

  readonly cases = rxResource({
    params: () => this.page(),
    stream: ({ params }) =>
      this.api.listarCasosRevision(params, PAGE_SIZE, 'body', false, {
        context: new HttpContext().set(HANDLED_PROBLEMS, ['INSUFFICIENT_ROLE']),
      }),
  });
  readonly productOptions = computed<DsOption<string>[]>(() =>
    (this.cases.hasValue() ? this.cases.value().productos : []).map((product) => ({
      value: product.id,
      label: productNames[product.tipo],
      description: this.productRange(product),
    })),
  );

  readonly selected = signal<CasoRevision | null>(null);
  readonly saving = signal(false);
  readonly problem = signal<SubmitProblem>(null);
  readonly decisionError = signal(false);
  readonly productError = signal('');
  readonly justificationError = signal('');
  decision: ResolucionNuevaDecisionEnum | null = null;
  productId: string | null = null;
  justification = '';

  status(error: unknown): number {
    return error instanceof HttpErrorResponse ? error.status : 0;
  }

  /** La evaluación guarda el monto sin moneda: se muestra el número, sin inventar el símbolo. */
  amount(item: CasoRevision): string {
    if (!item.montoSolicitado) return '—';
    return isMasked(item.montoSolicitado)
      ? item.montoSolicitado
      : this.sensitive.amount(AMOUNT.format(Number(item.montoSolicitado)));
  }

  open(item: CasoRevision): void {
    this.selected.set(item);
    this.decision = null;
    this.productId = null;
    this.justification = '';
    this.resetErrors();
    this.problem.set(null);
    this.dialog().show();
  }

  closed(): void {
    this.selected.set(null);
  }

  /** Nada se envía si falta la decisión, el producto al aprobar o la justificación. */
  submit(item: CasoRevision): void {
    if (this.saving()) return;
    this.resetErrors();
    const justification = this.justification.trim();
    const approving = this.decision === this.approve;
    if (!this.decision) this.decisionError.set(true);
    if (approving && !this.productId) this.productError.set('Elige el producto que se otorga.');
    if (justification.length < MIN_JUSTIFICATION) {
      this.justificationError.set(
        justification
          ? `Escribe al menos ${MIN_JUSTIFICATION} caracteres (llevas ${justification.length}).`
          : 'La justificación es obligatoria.',
      );
    }
    if (this.decisionError() || this.productError() || this.justificationError()) {
      this.focusFirstInvalid();
      return;
    }
    if (approving && !this.productOptions().length) return;

    this.saving.set(true);
    this.problem.set(null);
    const context = new HttpContext().set(HANDLED_PROBLEMS, [
      'VALIDATION_ERROR',
      'CONCURRENT_UPDATE',
      'ASSESSMENT_NOT_FOUND',
      'PRODUCT_NOT_FOUND',
      'INSUFFICIENT_ROLE',
    ]);
    this.api
      .resolverCasoRevision(
        item.evaluacionId,
        {
          decision: this.decision!,
          justificacion: justification,
          ...(approving ? { productoId: this.productId! } : {}),
        },
        'body',
        false,
        { context },
      )
      .subscribe({
        next: () => {
          this.saving.set(false);
          const product = this.productOptions().find((option) => option.value === this.productId);
          this.toast.show(approving ? `Caso aprobado con ${product?.label}.` : 'Caso rechazado.');
          this.dialog().close();
          this.refreshAfterResolving();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const errorCode = code(error);
          if (errorCode === 'CONCURRENT_UPDATE' || errorCode === 'ASSESSMENT_NOT_FOUND') {
            this.problem.set('resolved');
          } else if (errorCode === 'PRODUCT_NOT_FOUND') {
            this.problem.set('product');
            this.productId = null;
            this.cases.reload();
          } else if (errorCode === 'VALIDATION_ERROR') {
            this.problem.set('invalid');
          } else if (this.status(error) === 403) {
            this.problem.set('forbidden');
          } else {
            this.problem.set('error');
          }
        },
      });
  }

  backToQueue(): void {
    this.dialog().close();
    this.refreshAfterResolving();
  }

  private refreshAfterResolving(): void {
    // Si se resolvió el único caso de la última página, se vuelve a la anterior.
    const value = this.cases.hasValue() ? this.cases.value() : null;
    if (value && value.items.length === 1 && this.page() > 1) this.page.update((page) => page - 1);
    else this.cases.reload();
    // La fila (y su botón, que tenía el foco antes del diálogo) desaparece: el foco va al título.
    this.heading().nativeElement.focus();
  }

  private resetErrors(): void {
    this.decisionError.set(false);
    this.productError.set('');
    this.justificationError.set('');
  }

  private focusFirstInvalid(): void {
    const target = this.decisionError()
      ? this.document.querySelector<HTMLElement>('input[name="decision"]')
      : this.productError()
        ? this.document.getElementById('revision-producto')
        : this.document.getElementById('revision-justificacion');
    target?.focus();
  }

  private productRange(product: Producto): string {
    const range = (amount: string) => money({ amount, currency: product.moneda });
    return `${range(product.montoMinimo)} – ${range(product.montoMaximo)} · ${product.plazoMinimoMeses} a ${product.plazoMaximoMeses} meses`;
  }
}
