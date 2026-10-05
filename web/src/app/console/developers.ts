import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ClavesApiService } from '../api/api/clavesApi.service';
import { ClaveApi } from '../api/model/claveApi';
import { ClaveApiCreada } from '../api/model/claveApiCreada';
import { ClaveApiNuevaEntornoEnum } from '../api/model/claveApiNueva';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsDialog,
  DsEmptyState,
  DsField,
  DsIcon,
  DsInput,
  DsOption,
  DsProgress,
  DsSelect,
  DsSkeleton,
  DsTable,
  DsToastService,
} from '../design-system';

type KeysState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; keys: ClaveApi[] };

@Component({
  selector: 'dc-developers',
  imports: [
    DatePipe,
    FormsModule,
    DsAlert,
    DsBadge,
    DsButton,
    DsCard,
    DsDialog,
    DsEmptyState,
    DsField,
    DsIcon,
    DsInput,
    DsProgress,
    DsSelect,
    DsSkeleton,
    DsTable,
  ],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Desarrolladores</h1>
        <p class="ds-muted">Claves de API, webhooks y límites de uso de tu institución.</p>
      </div>
      <button dsButton (click)="openCreate()"><ds-icon name="key" />Crear clave</button>
    </header>

    <ds-card>
      <div class="card-head"><h2>Claves de API</h2></div>
      @switch (keys().status) {
        @case ('loading') {
          <div class="ds-stack-sm" aria-busy="true" aria-label="Cargando claves de API">
            <ds-skeleton /><ds-skeleton /><ds-skeleton />
          </div>
        }
        @case ('error') {
          <ds-alert tone="danger" heading="No se pudieron cargar las claves">
            <p>Revisa tu conexión e intenta de nuevo. Tus claves no cambiaron.</p>
            <button dsButton variant="secondary" (click)="load()">Reintentar</button>
          </ds-alert>
        }
        @case ('ready') {
          @if (list().length === 0) {
            <ds-empty-state
              icon="key"
              heading="Todavía no hay claves"
              description="Crea una clave para que tu sistema llame a la API de DoggyCredit."
            >
              <button dsButton variant="secondary" (click)="openCreate()">
                <ds-icon name="plus" />Crear primera clave
              </button>
            </ds-empty-state>
          } @else {
            <div class="ds-table-scroll" tabindex="0" aria-label="Claves de API">
              <table dsTable>
                <caption class="ds-sr-only">Claves de API vigentes</caption>
                <thead>
                  <tr>
                    <th scope="col">Nombre</th>
                    <th scope="col">Client ID</th>
                    <th scope="col">Secreto</th>
                    <th scope="col">Entorno</th>
                    <th scope="col">Creada</th>
                    <th scope="col"><span class="ds-sr-only">Acciones</span></th>
                  </tr>
                </thead>
                <tbody>
                  @for (key of list(); track key.id) {
                    <tr>
                      <th scope="row">{{ key.nombre }}</th>
                      <td class="ds-mono">{{ key.clientId }}</td>
                      <td class="ds-mono">••••{{ key.sufijoSecreto }}</td>
                      <td>
                        @if (key.entorno === 'produccion') {
                          <ds-badge tone="success">Producción</ds-badge>
                        } @else {
                          <ds-badge>Sandbox</ds-badge>
                        }
                      </td>
                      <td>{{ key.creadaEn | date: 'dd/MM/yyyy HH:mm' }}</td>
                      <td>
                        <button
                          dsButton
                          variant="quiet"
                          [attr.aria-label]="'Revocar la clave ' + key.nombre"
                          (click)="askRevoke(key)"
                        >
                          Revocar
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        }
      }
    </ds-card>

    <div class="split">
      <ds-card>
        <div class="card-head"><h2>Primera llamada</h2><span class="ds-caption">OAuth 2.0 · REST</span></div>
        <pre class="code"><code>{{ sample }}</code></pre>
      </ds-card>
      <div class="ds-stack">
        <ds-card>
          <div class="card-head"><h2>Uso de la API</h2></div>
          <ds-progress label="Solicitudes por minuto" [value]="18" [max]="60" />
          <p class="ds-caption">Límite del plan Gratis: 60 por minuto.</p>
        </ds-card>
        <ds-card>
          <div class="card-head"><h2>Webhooks</h2><ds-badge tone="info">Plan Pro</ds-badge></div>
          <p class="ds-muted">Recibe eventos como evaluacion.completada o consentimiento.revocado.</p>
        </ds-card>
      </div>
    </div>

    <ds-dialog #createDialog heading="Crear clave de API">
      <form class="ds-stack" (ngSubmit)="create()">
        @if (createError()) {
          <ds-alert tone="danger" heading="No se creó la clave" urgent>{{ createError() }}</ds-alert>
        }
        <ds-field label="Nombre" hint="Para reconocerla después, p. ej. App móvil." required>
          <input dsInput name="keyName" required maxlength="80" [(ngModel)]="keyName" />
        </ds-field>
        <ds-field label="Entorno">
          <ds-select name="env" [options]="environments" [(ngModel)]="env" />
        </ds-field>
        <div class="ds-actions">
          <button dsButton variant="secondary" (click)="createDialog.close()">Cancelar</button>
          <button dsButton type="submit" [busy]="creating()" [disabled]="creating()">Crear clave</button>
        </div>
      </form>
    </ds-dialog>

    <ds-dialog #secretDialog heading="Clave creada" (dismissed)="created.set(null)">
      @if (created(); as key) {
        <div class="ds-stack">
          <ds-alert tone="warning" heading="Copia el secreto ahora">
            No volveremos a mostrarlo. Si lo pierdes, revoca esta clave y crea otra.
          </ds-alert>
          <dl class="facts">
            <dt>Client ID</dt>
            <dd class="ds-mono">{{ key.clientId }}</dd>
            <dt>Secreto</dt>
            <dd class="ds-mono">{{ key.secreto }}</dd>
            <dt>Token</dt>
            <dd class="ds-mono">{{ key.tokenUrl }}</dd>
          </dl>
          <div class="ds-actions">
            <button dsButton variant="secondary" (click)="copySecret(key.secreto)">
              Copiar secreto
            </button>
            <button dsButton (click)="secretDialog.close()">Listo</button>
          </div>
        </div>
      }
    </ds-dialog>

    <ds-dialog #revokeDialog heading="Revocar clave de API">
      @if (revoking(); as key) {
        <div class="ds-stack">
          <p>
            Las integraciones que usen <strong>{{ key.nombre }}</strong> ({{ key.clientId }}) dejarán de
            obtener tokens. Los tokens ya emitidos vencen en minutos.
          </p>
          <div class="ds-actions">
            <button dsButton variant="secondary" (click)="revokeDialog.close()">Cancelar</button>
            <button dsButton variant="danger" [busy]="revokeBusy()" [disabled]="revokeBusy()" (click)="revoke(key)">
              Revocar clave
            </button>
          </div>
        </div>
      }
    </ds-dialog>
  `,
})
export class Developers implements OnInit {
  private readonly api = inject(ClavesApiService);
  private readonly toast = inject(DsToastService);
  readonly createDialog = viewChild.required<DsDialog>('createDialog');
  readonly secretDialog = viewChild.required<DsDialog>('secretDialog');
  readonly revokeDialog = viewChild.required<DsDialog>('revokeDialog');

  readonly keys = signal<KeysState>({ status: 'loading' });
  readonly creating = signal(false);
  readonly createError = signal('');
  readonly created = signal<ClaveApiCreada | null>(null);
  readonly revoking = signal<ClaveApi | null>(null);
  readonly revokeBusy = signal(false);

  readonly environments: DsOption<ClaveApiNuevaEntornoEnum>[] = [
    { value: ClaveApiNuevaEntornoEnum.Sandbox, label: 'Sandbox', description: 'Datos de prueba' },
    { value: ClaveApiNuevaEntornoEnum.Produccion, label: 'Producción', description: 'Requiere el plan Pro' },
  ];
  keyName = '';
  env = ClaveApiNuevaEntornoEnum.Sandbox;

  readonly sample = [
    '# 1. Token con tu clave (client credentials)',
    'curl -X POST <token-url> \\',
    '  -d grant_type=client_credentials \\',
    '  -d client_id=<client-id> -d client_secret=<secreto>',
    '',
    '# 2. Evaluación de crédito',
    'curl -X POST https://<tu-dominio>/api/v1/credit-assessments \\',
    '  -H "Authorization: Bearer <access_token>" \\',
    '  -H "Idempotency-Key: <uuid>" -H "Content-Type: application/json" \\',
    `  -d '{"titularDocumento": "…", "consentId": "…", "montoSolicitado": "15000", "plazoMeses": 12}'`,
  ].join('\n');

  ngOnInit(): void {
    this.load();
  }

  list(): ClaveApi[] {
    const state = this.keys();
    return state.status === 'ready' ? state.keys : [];
  }

  load(): void {
    this.keys.set({ status: 'loading' });
    this.api.listarClavesApi().subscribe({
      next: (keys) => this.keys.set({ status: 'ready', keys }),
      error: () => this.keys.set({ status: 'error' }),
    });
  }

  openCreate(): void {
    this.keyName = '';
    this.env = ClaveApiNuevaEntornoEnum.Sandbox;
    this.createError.set('');
    this.createDialog().show();
  }

  create(): void {
    const nombre = this.keyName.trim();
    if (nombre.length < 2) {
      this.createError.set('Escribe un nombre de al menos 2 caracteres.');
      return;
    }
    this.creating.set(true);
    this.createError.set('');
    this.api.crearClaveApi({ nombre, entorno: this.env }).subscribe({
      next: (key) => {
        this.creating.set(false);
        this.createDialog().close();
        this.created.set(key);
        this.secretDialog().show();
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.creating.set(false);
        this.createError.set(
          error.error?.code === 'FEATURE_NOT_IN_PLAN'
            ? 'Las claves de producción requieren el plan Pro. Puedes crear una clave de sandbox.'
            : 'No pudimos crearla ahora. Intenta de nuevo en unos minutos.',
        );
      },
    });
  }

  async copySecret(secret: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(secret);
      this.toast.show('Secreto copiado.');
    } catch {
      this.toast.show('No se pudo copiar. Selecciona el secreto y cópialo a mano.');
    }
  }

  askRevoke(key: ClaveApi): void {
    this.revoking.set(key);
    this.revokeDialog().show();
  }

  revoke(key: ClaveApi): void {
    this.revokeBusy.set(true);
    this.api.revocarClaveApi(key.id).subscribe({
      next: () => {
        this.revokeBusy.set(false);
        this.revokeDialog().close();
        this.toast.show(`Clave ${key.nombre} revocada.`);
        this.load();
      },
      error: () => {
        this.revokeBusy.set(false);
        this.revokeDialog().close();
        this.toast.show('No se pudo revocar la clave. Sigue vigente; intenta de nuevo.');
      },
    });
  }
}
