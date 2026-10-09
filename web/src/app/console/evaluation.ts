import { Component, ElementRef, Injector, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, catchError, firstValueFrom, map, of, switchMap, throwError } from 'rxjs';
import {
  ConsentimientoNuevoFuentesEnum,
  ConsentimientosService,
  Evaluacion,
  EvaluacionDecisionEnum,
  EvaluacionEstadoEnum,
  EvaluacionesService,
  FuenteIntegracion,
  Integracion,
  IntegracionEstadoEnum,
  IntegracionesService,
  Segmento,
  SolicitanteNuevoMonedaEnum,
  SolicitantesService,
} from '../api';
import { canAccessAppRoute } from '../auth/permissions';
import { AuthSessionService } from '../auth/session.service';
import {
  DsAlert,
  DsBadge,
  DsButton,
  DsCard,
  DsCheckbox,
  DsField,
  DsIcon,
  DsIconName,
  DsInput,
  DsLink,
  DsNumberField,
  DsOption,
  DsSelect,
  DsSkeleton,
  DsStat,
  DsSteps,
  DsTone,
} from '../design-system';
import { money } from './client-format';
import { TenantUsageService } from './tenant-usage.service';

const SEGMENTS: DsOption<Segmento>[] = [
  { value: Segmento.Persona, label: 'Persona' },
  { value: Segmento.Emprendedor, label: 'Emprendedor' },
  { value: Segmento.Pyme, label: 'PYME' },
];

const PURPOSES: DsOption<string>[] = [
  { value: 'Capital de trabajo', label: 'Capital de trabajo' },
  { value: 'Consumo o consolidación', label: 'Consumo o consolidación' },
  { value: 'Eficiencia energética', label: 'Eficiencia energética', description: 'Puede calificar a crédito ecológico' },
  { value: 'Inversión productiva', label: 'Inversión productiva' },
];

const SOURCES: Record<FuenteIntegracion, { name: string; icon: DsIconName }> = {
  [FuenteIntegracion.Buro]: { name: 'Buró de crédito', icon: 'database' },
  [FuenteIntegracion.Banco]: { name: 'Core bancario', icon: 'building' },
  [FuenteIntegracion.Wallet]: { name: 'Billetera móvil', icon: 'wallet' },
  [FuenteIntegracion.OpenBanking]: { name: 'Agregador open banking', icon: 'plug' },
  [FuenteIntegracion.Facturacion]: { name: 'Facturación electrónica', icon: 'store' },
  [FuenteIntegracion.Garantias]: { name: 'Registro de garantías', icon: 'shield' },
};

const SOURCE_STATUS: Record<IntegracionEstadoEnum, { label: string; tone: DsTone }> = {
  [IntegracionEstadoEnum.Conectado]: { label: 'Conectado', tone: 'success' },
  [IntegracionEstadoEnum.Disponible]: { label: 'Disponible', tone: 'neutral' },
  [IntegracionEstadoEnum.RequierePro]: { label: 'Plan Pro', tone: 'info' },
  [IntegracionEstadoEnum.Proximamente]: { label: 'Próximamente', tone: 'neutral' },
};

/** Nunca hay rechazo automático: ninguna decisión es un rechazo. */
const DECISION: Record<EvaluacionDecisionEnum, { label: string; tone: DsTone }> = {
  [EvaluacionDecisionEnum.Preaprobado]: { label: 'Preaprobado', tone: 'success' },
  [EvaluacionDecisionEnum.RevisionManual]: { label: 'Revisión manual', tone: 'warning' },
  [EvaluacionDecisionEnum.SolicitarDatos]: { label: 'Solicitar datos', tone: 'neutral' },
};

/** Avisos del paso de evaluación según el código del catálogo de errores. */
type Problem = 'quota' | 'consent' | 'error' | null;

const code = (error: unknown): string =>
  error instanceof HttpErrorResponse && error.error && typeof error.error === 'object'
    ? String((error.error as Record<string, unknown>)['code'] ?? '')
    : '';

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
    DsSelect,
    DsSkeleton,
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
            <h2 #stepHeading tabindex="-1">Solicitante</h2>
            <div class="filters">
              <ds-field label="Segmento">
                <ds-select name="segment" [options]="segments" [(ngModel)]="segment" />
              </ds-field>
              <ds-field label="Documento de identidad o NIT" required>
                <input dsInput name="document" [(ngModel)]="document" autocomplete="off" />
              </ds-field>
            </div>
            <div class="filters">
              <ds-field label="Actividad económica" required>
                <input dsInput name="activity" [(ngModel)]="activity" autocomplete="off" />
              </ds-field>
              <ds-field label="Antigüedad de la actividad">
                <ds-number-field name="seniority" suffix="meses" [min]="0" [max]="1200" [(ngModel)]="seniority" />
              </ds-field>
              <ds-field label="Ingreso mensual declarado" required>
                <ds-number-field name="income" prefix="Bs" [min]="0" [step]="100" [(ngModel)]="income" />
              </ds-field>
            </div>
            <div class="filters">
              <ds-field label="Monto solicitado">
                <ds-number-field name="amount" prefix="Bs" [min]="0" [step]="500" [(ngModel)]="amount" />
              </ds-field>
              <ds-field label="Plazo">
                <ds-number-field name="term" suffix="meses" [min]="1" [max]="360" [(ngModel)]="term" />
              </ds-field>
            </div>
            <ds-field label="Destino del crédito" hint="Ayuda a detectar la necesidad real." required>
              <ds-select name="purpose" placeholder="Elige un destino" [options]="purposes" [(ngModel)]="purpose" />
            </ds-field>
            @if (invalid()) {
              <ds-alert tone="warning" heading="Faltan datos del solicitante">
                Completa documento (3 caracteres o más), actividad, ingreso mensual y destino del crédito.
              </ds-alert>
            }
          }
          @case (1) {
            <h2 #stepHeading tabindex="-1">Consentimiento</h2>
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
            @if (problem() === 'consent') {
              <ds-alert tone="warning" heading="El consentimiento no es válido">
                Vence o fue revocado. Registra un consentimiento nuevo para continuar.
              </ds-alert>
            }
          }
          @case (2) {
            <h2 #stepHeading tabindex="-1">Fuentes</h2>
            @if (sources() === null) {
              <div aria-busy="true" aria-label="Cargando fuentes"><ds-skeleton /></div>
            } @else if (sources()!.length) {
              <ul class="list">
                @for (source of sources()!; track source.fuente) {
                  <li>
                    <span class="ds-row"><span class="icon-tile"><ds-icon [name]="sourceInfo(source).icon" /></span>{{ sourceInfo(source).name }}</span>
                    <ds-badge [tone]="sourceStatus(source).tone">{{ sourceStatus(source).label }}</ds-badge>
                  </li>
                }
              </ul>
            } @else {
              <p class="ds-muted">No pudimos cargar el estado de las fuentes; la evaluación igual consulta las conectadas.</p>
            }
            <p class="ds-caption">Solo se consultan las fuentes conectadas y autorizadas por el cliente.</p>
            @if (problem() === 'quota') {
              <ds-alert tone="warning" heading="Llegaste al límite de evaluaciones de tu plan">
                Pausamos las nuevas evaluaciones hasta el próximo mes.
                @if (canManagePlan()) {
                  <a dsLink routerLink="/app/configuracion" fragment="plan">Ver tu plan</a>
                } @else {
                  Pide a quien administra tu institución que revise el plan.
                }
              </ds-alert>
            }
            @if (problem() === 'error') {
              <ds-alert tone="danger" heading="No pudimos completar la evaluación">
                Revisa tu conexión y vuelve a intentarlo: no se duplicará la evaluación.
              </ds-alert>
            }
          }
          @case (3) {
            @if (result(); as r) {
              <div class="card-head">
                <h2 #stepHeading tabindex="-1">Resultado</h2>
                @if (r.simulado) {
                  <ds-badge tone="warning">Datos simulados</ds-badge>
                }
              </div>
              @if (r.estado === parcial) {
                <ds-alert tone="warning" heading="Resultado parcial">
                  Una fuente no respondió a tiempo. No se inventó ningún dato y un resultado parcial
                  nunca se preaprueba: queda para revisión.
                </ds-alert>
              }
              <div class="kpis">
                <ds-stat label="Score" [value]="(r.score ?? '—') + ''" [detail]="'Banda ' + (r.banda ?? '—')" />
                <ds-stat label="Capacidad de pago" [value]="capacity()" detail="Mensual, del Perfil 360" />
                <ds-stat label="Necesidad" [value]="purpose ?? '—'" detail="Destino declarado" />
                <ds-stat label="Decisión" [value]="decisionLabel(r)" detail="Según tus políticas" />
              </div>
              @if (topReasons(r).length) {
                <div class="ds-stack-sm">
                  <h3>Principales razones</h3>
                  <ul class="list">
                    @for (reason of topReasons(r); track reason) {
                      <li class="ds-mono">{{ reason }}</li>
                    }
                  </ul>
                </div>
              }
              <ds-alert heading="Recomendación, no aprobación">
                La decisión final corresponde a tu política de crédito.
                <a dsLink [routerLink]="['/app/clientes', applicantId()]">Ver perfil completo</a>
              </ds-alert>
            }
          }
        }

        <div class="ds-actions">
          @if (step() > 0 && step() < steps.length - 1) {
            <button dsButton type="button" variant="secondary" [disabled]="busy()" (click)="back()">Atrás</button>
          }
          @if (step() < steps.length - 1) {
            <button dsButton type="submit" [busy]="busy()">{{ step() === 2 ? 'Evaluar' : 'Continuar' }}</button>
          } @else {
            <button dsButton type="button" (click)="restart()">Nueva evaluación</button>
          }
        </div>
      </form>
    </ds-card>
  `,
})
export class Evaluation {
  private readonly applicants = inject(SolicitantesService);
  private readonly consents = inject(ConsentimientosService);
  private readonly integrations = inject(IntegracionesService);
  private readonly assessments = inject(EvaluacionesService);
  private readonly auth = inject(AuthSessionService);
  private readonly quota = inject(TenantUsageService);
  private readonly injector = inject(Injector);
  private readonly stepHeading = viewChild<ElementRef<HTMLElement>>('stepHeading');

  readonly steps = ['Solicitante', 'Consentimiento', 'Fuentes', 'Resultado'];
  readonly segments = SEGMENTS;
  readonly purposes = PURPOSES;
  readonly parcial = EvaluacionEstadoEnum.Parcial;

  readonly step = signal(0);
  readonly busy = signal(false);
  readonly invalid = signal(false);
  readonly missingConsent = signal(false);
  readonly problem = signal<Problem>(null);
  readonly applicantId = signal<string | null>(null);
  readonly consentId = signal<string | null>(null);
  /** null mientras carga; [] si no se pudo cargar. */
  readonly sources = signal<Integracion[] | null>(null);
  readonly result = signal<Evaluacion | null>(null);
  readonly capacity = signal('—');
  readonly canManagePlan = computed(() => canAccessAppRoute('/app/configuracion', this.auth.session().roles));

  segment: Segmento = Segmento.Persona;
  document = '';
  activity = '';
  seniority: number | null = 0;
  income: number | null = null;
  amount: number | null = null;
  term: number | null = 12;
  purpose: string | null = null;
  // Nada preseleccionado: el cliente elige cada permiso.
  consentBureau = false;
  consentAccounts = false;
  consentWallet = false;
  /** Se conserva entre reintentos para que un doble clic o un reintento no dupliquen la evaluación. */
  private idempotencyKey = crypto.randomUUID();

  async next(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      switch (this.step()) {
        case 0:
          await this.saveApplicant();
          break;
        case 1:
          await this.saveConsent();
          break;
        case 2:
          await this.evaluate();
          break;
      }
    } finally {
      this.busy.set(false);
    }
  }

  back(): void {
    this.problem.set(null);
    this.goTo(Math.max(0, this.step() - 1));
  }

  restart(): void {
    this.goTo(0);
    this.applicantId.set(null);
    this.consentId.set(null);
    this.result.set(null);
    this.capacity.set('—');
    this.problem.set(null);
    this.document = '';
    this.activity = '';
    this.seniority = 0;
    this.income = null;
    this.amount = null;
    this.term = 12;
    this.purpose = null;
    this.consentBureau = false;
    this.consentAccounts = false;
    this.consentWallet = false;
    this.idempotencyKey = crypto.randomUUID();
  }

  /** Al cambiar de paso el botón pulsado puede desaparecer: el foco pasa al título del paso nuevo. */
  private goTo(step: number): void {
    this.step.set(step);
    afterNextRender(() => this.stepHeading()?.nativeElement.focus(), { injector: this.injector });
  }

  sourceInfo(source: Integracion) {
    return SOURCES[source.fuente];
  }

  sourceStatus(source: Integracion) {
    return SOURCE_STATUS[source.estado];
  }

  decisionLabel(result: Evaluacion): string {
    return result.decision ? DECISION[result.decision].label : '—';
  }

  /** Las 3 razones de mayor impacto (la API ya las devuelve ordenadas). */
  topReasons(result: Evaluacion): string[] {
    return (result.razones ?? []).slice(0, 3);
  }

  /** Paso 1: reutiliza el solicitante si el documento ya existe; si no, lo crea. */
  private async saveApplicant(): Promise<void> {
    const document = this.document.trim();
    const activity = this.activity.trim();
    const valid = document.length >= 3 && activity.length >= 2 && (this.income ?? 0) > 0 && !!this.purpose;
    this.invalid.set(!valid);
    if (!valid) return;
    try {
      const id = await firstValueFrom(this.findApplicant(document).pipe(
        switchMap((existing) => (existing ? of(existing) : this.createApplicant(document, activity))),
      ));
      this.applicantId.set(id);
      this.goTo(1);
    } catch {
      // El interceptor ya avisó con el detalle del error (RFC 9457).
    }
  }

  private findApplicant(document: string): Observable<string | null> {
    // El documento viaja en la cabecera X-Documento, nunca en la URL (B-08).
    return this.applicants.listarSolicitantes(1, 1, undefined, document).pipe(
      map((page) => page.items[0]?.id ?? null),
    );
  }

  private createApplicant(document: string, activity: string): Observable<string> {
    return this.applicants
      .crearSolicitante({
        documento: document,
        actividad: activity,
        antiguedadActividadMeses: Math.max(0, Math.round(this.seniority ?? 0)),
        ingresosMensuales: (this.income ?? 0).toFixed(2),
        moneda: SolicitanteNuevoMonedaEnum.Bob,
        destinoCredito: this.purpose!,
        segmento: this.segment,
      })
      .pipe(
        map((created) => created.id),
        // Alta simultánea del mismo documento (409): se reutiliza el que ganó.
        catchError((error: unknown) =>
          code(error) === 'APPLICANT_ALREADY_EXISTS'
            ? this.findApplicant(document).pipe(
                switchMap((id) => (id ? of(id) : throwError(() => error))),
              )
            : throwError(() => error),
        ),
      );
  }

  /** Paso 2: registra el consentimiento con las fuentes que el cliente autorizó. */
  private async saveConsent(): Promise<void> {
    this.missingConsent.set(!this.consentBureau);
    if (!this.consentBureau) return;
    const fuentes = [ConsentimientoNuevoFuentesEnum.Buro];
    if (this.consentAccounts) fuentes.push(ConsentimientoNuevoFuentesEnum.Banco);
    if (this.consentWallet) fuentes.push(ConsentimientoNuevoFuentesEnum.Wallet);
    try {
      const consent = await firstValueFrom(this.consents.crearConsentimiento({
        titularDocumento: this.document.trim(),
        fuentes,
        finalidad: 'Evaluación de crédito',
        vigenciaDias: 90,
      }));
      this.consentId.set(consent.id ?? null);
      this.problem.set(null);
      this.goTo(2);
      this.loadSources();
    } catch {
      // El interceptor ya avisó con el detalle del error.
    }
  }

  private loadSources(): void {
    this.sources.set(null);
    this.integrations.listarIntegraciones().subscribe({
      next: (sources) => this.sources.set(sources),
      error: () => this.sources.set([]),
    });
  }

  /** Paso 3: evalúa con una Idempotency-Key estable entre reintentos. */
  private async evaluate(): Promise<void> {
    this.problem.set(null);
    try {
      const result = await firstValueFrom(this.assessments.crearEvaluacion(this.idempotencyKey, {
        titularDocumento: this.document.trim(),
        consentId: this.consentId()!,
        proposito: this.purpose ?? undefined,
        montoSolicitado: this.amount ? this.amount.toFixed(2) : undefined,
        plazoMeses: this.term ?? undefined,
      }));
      this.result.set(result);
      this.goTo(3);
      this.loadCapacity();
      // La evaluación consumió cuota: la barra y los avisos del 80 % y 100 % se actualizan (E-11).
      this.quota.refresh();
    } catch (error) {
      const errorCode = code(error);
      if (errorCode === 'QUOTA_EXCEEDED') {
        this.problem.set('quota');
        this.quota.refresh();
      } else if (errorCode === 'CONSENT_REQUIRED') {
        // El consentimiento venció o fue revocado: hay que registrar uno nuevo.
        this.problem.set('consent');
        this.consentId.set(null);
        this.goTo(1);
      } else {
        this.problem.set('error');
      }
    }
  }

  /** La capacidad de pago sale del Perfil 360 que la evaluación acaba de consolidar. */
  private loadCapacity(): void {
    const id = this.applicantId();
    if (!id) return;
    this.applicants.obtenerSolicitante(id).subscribe({
      next: (detail) => this.capacity.set(money(detail.perfil?.capacidadPago)),
      error: () => this.capacity.set('—'),
    });
  }
}
