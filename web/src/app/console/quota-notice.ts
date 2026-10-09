import { Component, ElementRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { canAccessAppRoute } from '../auth/permissions';
import { AuthSessionService } from '../auth/session.service';
import { DsAlert, DsButton, DsLink, DsToastService } from '../design-system';
import { QuotaLevel, TenantUsageService } from './tenant-usage.service';

const number = new Intl.NumberFormat('es-BO');
const rank: Record<QuotaLevel, number> = { ok: 0, warning: 1, exhausted: 2 };
const percent = (used: number, limit: number) => Math.floor((used * 100) / limit);

/** sessionStorage: un aviso ocultado no vuelve en la sesión, pero el siguiente umbral sí aparece. */
const storageKey = (tenantId: string, level: QuotaLevel) => `dc-aviso-cuota:${tenantId}:${level}`;

function storedFlag(key: string): boolean {
  try {
    return sessionStorage.getItem(key) === '1';
  } catch {
    return false;
  }
}

/**
 * Avisos de cuota mensual (E-11, RF-PLN-06): al 80 % y al 100 % del límite del plan.
 * Sin cuota (cargando, error o plan sin límite) no muestra nada; la barra lateral
 * ya distingue la carga y el error del uso. Cruzar un umbral durante la sesión
 * (p. ej. al evaluar) además lo notifica con un toast: el aviso queda arriba de la
 * vista, fuera de pantalla si se había desplazado, y el toast se anuncia siempre.
 */
@Component({
  selector: 'dc-quota-notice',
  imports: [RouterLink, DsAlert, DsButton, DsLink],
  styles: ['.quota-notice { margin-bottom: var(--ds-space-8); }'],
  template: `
    @if (notice(); as n) {
      <div class="quota-notice">
        <ds-alert
          [tone]="n.level === 'exhausted' ? 'danger' : 'warning'"
          [heading]="n.heading"
          [urgent]="n.level === 'exhausted'"
        >
          <p>{{ n.detail }}</p>
          <div class="ds-row">
            @if (canManagePlan()) {
              <a dsLink appearance="secondary" routerLink="/app/configuracion" fragment="plan"
                >Ver tu plan</a
              >
            } @else {
              <span>Avisa a quien administra tu institución para revisar el plan.</span>
            }
            <button dsButton variant="quiet" (click)="dismiss(n.key)">Ocultar aviso</button>
          </div>
        </ds-alert>
      </div>
    }
  `,
})
export class QuotaNotice {
  private readonly quota = inject(TenantUsageService);
  private readonly auth = inject(AuthSessionService);
  private readonly toast = inject(DsToastService);
  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  private readonly dismissed = signal<ReadonlySet<string>>(new Set());
  private previousLevel: QuotaLevel | null = null;

  readonly canManagePlan = computed(() =>
    canAccessAppRoute('/app/configuracion', this.auth.session().roles),
  );

  readonly notice = computed(() => {
    const usage = this.quota.usage();
    const level = this.quota.level();
    if (!usage || !level || level === 'ok') return null;
    const key = storageKey(this.auth.session().tenantId, level);
    if (this.dismissed().has(key) || storedFlag(key)) return null;
    const used = number.format(usage.evaluacionesMes);
    const limit = number.format(usage.limiteMensual);
    return level === 'exhausted'
      ? {
          key,
          level,
          heading: 'Llegaste al límite de evaluaciones del mes',
          detail: `Usaste ${used} de ${limit}. Las evaluaciones nuevas se pausan hasta el próximo mes; las anteriores siguen disponibles.`,
        }
      : {
          key,
          level,
          heading: `Usaste el ${percent(usage.evaluacionesMes, usage.limiteMensual)} % de las evaluaciones del mes`,
          detail: `Llevas ${used} de ${limit}. Al llegar al límite, las evaluaciones nuevas se pausan hasta el próximo mes.`,
        };
  });

  constructor() {
    effect(() => {
      const usage = this.quota.usage();
      const level = this.quota.level();
      if (!usage || !level) return;
      const previous = this.previousLevel;
      this.previousLevel = level;
      // Solo al subir de umbral con la consola abierta; al entrar, basta el aviso.
      if (!previous || rank[level] <= rank[previous] || level === 'ok') return;
      untracked(() =>
        this.toast.show(
          level === 'exhausted'
            ? 'Llegaste al límite de evaluaciones del mes.'
            : `Usaste el ${percent(usage.evaluacionesMes, usage.limiteMensual)} % de las evaluaciones del mes.`,
        ),
      );
    });
  }

  dismiss(key: string): void {
    try {
      sessionStorage.setItem(key, '1');
    } catch {
      /* Almacenamiento opcional: se oculta igual durante esta vista. */
    }
    this.dismissed.update((keys) => new Set(keys).add(key));
    // El botón desaparece con el aviso: el foco vuelve al contenido principal.
    this.host.closest('main')?.focus();
  }
}
