import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { WebhooksService } from '../api/api/webhooks.service';
import { EventoWebhook } from '../api/model/eventoWebhook';
import { WebhookCambio } from '../api/model/webhookCambio';
import { WebhookConfiguracion } from '../api/model/webhookConfiguracion';
import {
  DsAlert, DsBadge, DsButton, DsCard, DsDialog, DsEmptyState, DsField, DsInput,
  DsOption, DsSelect, DsSkeleton, DsToastService,
} from '../design-system';

type State = { status: 'loading' | 'error' | 'restricted' } | { status: 'ready'; config: WebhookConfiguracion };

@Component({
  selector: 'dc-webhook-settings',
  imports: [FormsModule, DsAlert, DsBadge, DsButton, DsCard, DsDialog, DsEmptyState, DsField, DsInput, DsSelect, DsSkeleton],
  template: `
    <ds-card>
      <div class="ds-stack">
        <div class="ds-between"><h2>Webhooks</h2><ds-badge tone="info">Plan Pro</ds-badge></div>
        <p class="ds-muted">Recibe evaluaciones completadas y cambios de consentimiento en tu aplicación.</p>
        @switch (state().status) {
          @case ('loading') {
            <div class="ds-stack-sm" aria-busy="true" aria-label="Cargando Webhooks"><ds-skeleton /><ds-skeleton /></div>
          }
          @case ('error') {
            <ds-alert tone="danger" heading="No se pudieron cargar los Webhooks">
              <p>Revisa tu conexión e intenta de nuevo.</p>
              <button dsButton variant="secondary" (click)="load()">Reintentar Webhooks</button>
            </ds-alert>
          }
          @case ('restricted') {
            <ds-alert tone="info" heading="Webhooks disponibles con Pro">
              Tu plan no incluye la entrega de eventos. Puedes mejorar el plan desde Configuración.
            </ds-alert>
          }
          @case ('ready') {
            @if (!configuration()?.url) {
              <ds-empty-state heading="Sin Webhooks configurados" description="Agrega tu receptor y elige los eventos que necesita tu aplicación." />
            }
            <form class="ds-stack" (ngSubmit)="save()">
              @if (error()) {
                <ds-alert tone="danger" heading="No se guardaron los Webhooks" urgent>{{ error() }}</ds-alert>
              }
              <ds-field label="URL del receptor" hint="Dirección HTTPS de tu aplicación." required>
                <input dsInput name="webhookUrl" maxlength="2048" [(ngModel)]="url" [disabled]="saving()" />
              </ds-field>
              <ds-field label="Eventos suscritos" required>
                <ds-select name="webhookEvents" multiple [options]="events" [(ngModel)]="selected" [disabled]="saving()" />
              </ds-field>
              <ds-field label="Secreto de firma" [required]="!configuration()?.secretoConfigurado"
                hint="Entre 32 y 128 caracteres. Compártelo con tu receptor; déjalo vacío para conservar el actual.">
                <input dsInput type="password" name="webhookSecret" autocomplete="new-password" maxlength="128"
                  [(ngModel)]="secret" [disabled]="saving()" />
              </ds-field>
              @if (configuration()?.secretoConfigurado) {
                <p class="ds-caption">Hay un secreto configurado. No se muestra ni se devuelve al consultar.</p>
              }
              <div class="ds-actions">
                @if (configuration()?.url) {
                  <button dsButton variant="danger" [disabled]="saving()" (click)="confirmDisable()">Desactivar Webhooks</button>
                }
                <button dsButton type="submit" [busy]="saving()">Guardar Webhooks</button>
              </div>
            </form>
          }
        }
      </div>
    </ds-card>
    <ds-dialog #disableDialog heading="Desactivar Webhooks">
      <div class="ds-stack">
        <p>Dejarás de recibir eventos y se cancelarán las entregas pendientes. Una entrega ya en curso puede finalizar.
          Para activarlos otra vez necesitarás configurar el receptor y su secreto.</p>
        @if (error()) {
          <ds-alert tone="danger" heading="No se desactivaron los Webhooks" urgent>{{ error() }}</ds-alert>
        }
        <div class="ds-actions">
          <button dsButton variant="secondary" [disabled]="saving()" (click)="disableDialog.close()">Cancelar</button>
          <button dsButton variant="danger" [busy]="saving()" (click)="disable()">Desactivar Webhooks</button>
        </div>
      </div>
    </ds-dialog>
  `,
})
export class WebhookSettings implements OnInit {
  private readonly api = inject(WebhooksService);
  private readonly toast = inject(DsToastService);
  private readonly destroyRef = inject(DestroyRef);
  readonly disableDialog = viewChild.required<DsDialog>('disableDialog');
  readonly state = signal<State>({ status: 'loading' });
  readonly saving = signal(false);
  readonly error = signal('');
  readonly events: DsOption<EventoWebhook>[] = [
    { value: EventoWebhook.CreditAssessmentCompletedV1, label: 'Evaluación completada' },
    { value: EventoWebhook.ConsentGrantedV1, label: 'Consentimiento otorgado' },
    { value: EventoWebhook.ConsentRevokedV1, label: 'Consentimiento revocado' },
  ];
  url = '';
  selected: EventoWebhook[] = [];
  secret = '';

  ngOnInit(): void { this.load(); }

  configuration(): WebhookConfiguracion | null {
    const state = this.state();
    return state.status === 'ready' ? state.config : null;
  }

  load(): void {
    this.state.set({ status: 'loading' });
    this.api.obtenerWebhooks().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (config) => this.loaded(config),
      error: (error: HttpErrorResponse) => this.state.set({ status: this.restricted(error) ? 'restricted' : 'error' }),
    });
  }

  save(): void {
    if (this.saving() || this.state().status !== 'ready') return;
    try {
      const url = new URL(this.url.trim());
      if (url.protocol !== 'https:' || url.username || url.password || url.hash) throw new Error();
    } catch {
      this.error.set('Escribe una URL HTTPS válida, sin credenciales ni fragmentos.');
      return;
    }
    if (!this.selected.length) {
      this.error.set('Selecciona al menos un evento.');
      return;
    }
    if ((!this.secret && !this.configuration()?.secretoConfigurado)
      || this.secret && (this.secret.length < 32 || this.secret.length > 128)) {
      this.error.set('Escribe un secreto de entre 32 y 128 caracteres.');
      return;
    }
    this.persist({ url: this.url.trim(), eventos: this.selected, ...(this.secret ? { secreto: this.secret } : {}) }, false);
  }

  confirmDisable(): void {
    this.error.set('');
    this.disableDialog().show();
  }

  disable(): void {
    if (this.saving()) return;
    this.persist({ url: null, eventos: [] }, true);
  }

  private persist(body: WebhookCambio, disabling: boolean): void {
    this.saving.set(true);
    this.error.set('');
    this.api.actualizarWebhooks(body).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (config) => {
        this.saving.set(false);
        this.loaded(config);
        if (disabling) this.disableDialog().close();
        this.toast.show(disabling ? 'Webhooks desactivados.' : 'Webhooks guardados.');
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        if (this.restricted(error)) {
          this.state.set({ status: 'restricted' });
          if (disabling) this.disableDialog().close();
        } else {
          this.error.set(error.error?.code === 'VALIDATION_ERROR'
            ? 'Revisa la URL, los eventos y el secreto. Tus datos se conservaron.'
            : 'No pudimos guardar los cambios. Tus datos se conservaron; intenta de nuevo.');
        }
      },
    });
  }

  private loaded(config: WebhookConfiguracion): void {
    this.url = config.url ?? '';
    this.selected = [...config.eventos];
    this.secret = '';
    this.error.set('');
    this.state.set({ status: 'ready', config });
  }

  private restricted(error: HttpErrorResponse): boolean {
    return error.status === 403 && error.error?.code === 'FEATURE_NOT_IN_PLAN';
  }
}
