import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject, catchError, of, switchMap } from 'rxjs';
import { TenantService, UsoTenant } from '../api';

/** Umbrales de la cuota mensual (E-11): aviso desde el 80 % y límite al 100 %. */
export type QuotaLevel = 'ok' | 'warning' | 'exhausted';

export function quotaLevel({ evaluacionesMes: used, limiteMensual: limit }: UsoTenant): QuotaLevel {
  if (limit <= 0) return 'ok'; // 0 = plan sin límite mensual
  if (used >= limit) return 'exhausted';
  return used * 5 >= limit * 4 ? 'warning' : 'ok'; // ≥ 80 %, en enteros
}

/**
 * Uso mensual de evaluaciones del tenant (`GET /tenant/actual/uso`), compartido por
 * la barra, Configuración y los avisos de cuota. `refresh()` lo vuelve a consultar
 * sin vaciar el último valor, por ejemplo después de evaluar.
 */
@Injectable({ providedIn: 'root' })
export class TenantUsageService {
  private readonly api = inject(TenantService);
  private readonly reloads = new BehaviorSubject<void>(undefined);

  /** `undefined` mientras carga la primera vez; `null` si no se pudo consultar. */
  readonly usage = toSignal(
    this.reloads.pipe(
      switchMap(() => this.api.obtenerUsoTenant().pipe(catchError(() => of(null)))),
    ),
  );
  readonly level = computed<QuotaLevel | null>(() => {
    const usage = this.usage();
    return usage ? quotaLevel(usage) : null;
  });

  refresh(): void {
    this.reloads.next();
  }
}
