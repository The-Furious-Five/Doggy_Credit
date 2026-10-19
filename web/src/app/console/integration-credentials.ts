import { Component, DestroyRef, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import {
  CredencialesIntegracionesService,
  EstadoCredencialesIntegracion,
  ProveedorCredenciales,
} from '../api';
import {
  DsAlert,
  DsButton,
  DsDialog,
  DsField,
  DsInput,
  DsOption,
  DsSelect,
  DsSpinner,
  DsToastService,
} from '../design-system';

@Component({
  selector: 'dc-integration-credentials',
  imports: [FormsModule, DsAlert, DsButton, DsDialog, DsField, DsInput, DsSelect, DsSpinner],
  template: `
    <button
      dsButton
      variant="quiet"
      [attr.aria-label]="'Credenciales propias: ' + name()"
      (click)="open()"
    >
      Credenciales propias
    </button>
    <ds-dialog [heading]="'Credenciales propias · ' + name()" (dismissed)="dismiss()">
      @if (opened()) {
        <div class="ds-stack">
          @if (source() === bank) {
            <ds-field
              label="Proveedor bancario"
              hint="Configura la clave del proveedor que utiliza tu institución."
            >
              <ds-select
                [options]="banks"
                [ngModel]="provider()"
                (ngModelChange)="changeProvider($event)"
                [disabled]="busy()"
              />
            </ds-field>
          }
          @switch (state()) {
            @case ('loading') {
              <ds-spinner label="Consultando credenciales…" />
            }
            @case ('error') {
              <ds-alert tone="danger" heading="No pudimos consultar las credenciales" urgent>
                <p>Revisa tu conexión y vuelve a intentarlo.</p>
                <button dsButton variant="secondary" (click)="load()">Reintentar</button>
              </ds-alert>
            }
            @case ('ready') {
              @if (metadata()?.configuradas) {
                <p>
                  Clave propia configurada. Termina en
                  <strong>{{ metadata()?.ultimosCuatro }}</strong
                  >.
                </p>
              } @else {
                <p>Se utilizan las credenciales de DoggyCredit.</p>
              }
              <p class="ds-muted">
                La clave se guarda cifrada y no se puede volver a consultar. Cambiarla no conecta
                esta fuente.
              </p>
              @if (problem()) {
                <ds-alert tone="danger" heading="No pudimos guardar el cambio" urgent>
                  Revisa tu conexión. Si vas a guardar una clave, ingrésala nuevamente.
                </ds-alert>
              }
              <form class="ds-stack" (ngSubmit)="save()">
                <ds-field
                  label="Nueva clave API"
                  required
                  hint="De 8 a 2048 caracteres, sin espacios. Para reemplazarla, ingresa una nueva clave."
                  [error]="
                    invalid()
                      ? 'Ingresa de 8 a 2048 caracteres sin espacios ni saltos de línea.'
                      : ''
                  "
                >
                  <input
                    dsInput
                    type="password"
                    name="integrationKey"
                    autocomplete="new-password"
                    autocapitalize="off"
                    spellcheck="false"
                    [ngModel]="key"
                    (ngModelChange)="key = $event; invalid.set(false)"
                    [disabled]="busy()"
                    maxlength="2048"
                  />
                </ds-field>
                <div class="ds-row">
                  <button dsButton type="submit" [busy]="busy()" [disabled]="busy()">
                    Guardar clave
                  </button>
                  <button
                    dsButton
                    variant="secondary"
                    [disabled]="busy()"
                    (click)="dialog().close()"
                  >
                    Cancelar
                  </button>
                </div>
              </form>
              @if (metadata()?.configuradas) {
                <div class="ds-stack-sm">
                  <p class="ds-muted">
                    Al eliminarla, las próximas consultas usarán las credenciales de DoggyCredit.
                  </p>
                  <button dsButton variant="danger" [disabled]="busy()" (click)="remove()">
                    Eliminar credenciales propias
                  </button>
                </div>
              }
            }
          }
        </div>
      }
    </ds-dialog>
  `,
})
export class IntegrationCredentials {
  readonly source = input.required<ProveedorCredenciales>();
  readonly name = input.required<string>();
  readonly dialog = viewChild.required(DsDialog);
  private readonly api = inject(CredencialesIntegracionesService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toast = inject(DsToastService);
  private readonly closed = new Subject<void>();
  readonly bank = ProveedorCredenciales.Banco;
  readonly banks: DsOption<ProveedorCredenciales>[] = [
    { value: ProveedorCredenciales.Banco, label: 'Banco' },
    { value: ProveedorCredenciales.BancoDos, label: 'Segundo banco' },
  ];
  readonly provider = signal<ProveedorCredenciales>(ProveedorCredenciales.Buro);
  readonly metadata = signal<EstadoCredencialesIntegracion | null>(null);
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly opened = signal(false);
  readonly busy = signal(false);
  readonly problem = signal(false);
  readonly invalid = signal(false);
  key = '';

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.key = '';
      this.closed.next();
      this.closed.complete();
    });
  }

  open(): void {
    this.opened.set(true);
    this.provider.set(this.source());
    this.dialog().show();
    this.load();
  }

  dismiss(): void {
    this.opened.set(false);
    this.closed.next();
    this.key = '';
    this.busy.set(false);
    this.invalid.set(false);
    this.problem.set(false);
    this.metadata.set(null);
  }

  changeProvider(provider: ProveedorCredenciales): void {
    if (this.busy()) return;
    this.closed.next();
    this.provider.set(provider);
    this.load();
  }

  load(): void {
    this.key = '';
    this.invalid.set(false);
    this.problem.set(false);
    this.metadata.set(null);
    this.state.set('loading');
    this.api
      .obtenerCredencialesIntegracion(this.provider())
      .pipe(takeUntil(this.closed), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (metadata) => {
          this.metadata.set(metadata);
          this.state.set('ready');
        },
        error: () => this.state.set('error'),
      });
  }

  save(): void {
    if (this.busy() || this.state() !== 'ready') return;
    if (!/^[!-~]{8,2048}$/.test(this.key)) {
      this.invalid.set(true);
      return;
    }
    const key = this.key;
    this.key = '';
    this.busy.set(true);
    this.problem.set(false);
    this.api
      .guardarCredencialesIntegracion(this.provider(), { clave: key })
      .pipe(takeUntil(this.closed), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (metadata) => {
          this.metadata.set(metadata);
          this.busy.set(false);
          this.toast.show('Credenciales propias guardadas.');
        },
        error: () => {
          this.busy.set(false);
          this.problem.set(true);
        },
      });
  }

  remove(): void {
    if (this.busy() || this.state() !== 'ready' || !this.metadata()?.configuradas) return;
    this.key = '';
    this.busy.set(true);
    this.problem.set(false);
    this.api
      .eliminarCredencialesIntegracion(this.provider())
      .pipe(takeUntil(this.closed), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.metadata.set({ configuradas: false, ultimosCuatro: '' });
          this.busy.set(false);
          this.toast.show('Se usarán las credenciales de DoggyCredit.');
        },
        error: () => {
          this.busy.set(false);
          this.problem.set(true);
        },
      });
  }
}
