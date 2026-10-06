import { performance } from 'node:perf_hooks';
import { setTimeout as sleep } from 'node:timers/promises';
import type { Page } from '@playwright/test';

/** Cola del cliente: espacia peticiones sin reintentar ni modificar respuestas HTTP. */
export class RequestPacer {
  private tail: Promise<void> = Promise.resolve();
  private lastTurn = Number.NEGATIVE_INFINITY;

  constructor(
    private readonly intervalMs: number,
    private readonly now: () => number = () => performance.now(),
    private readonly delay: (ms: number) => Promise<unknown> = sleep,
  ) {}

  wait(): Promise<void> {
    const turn = this.tail.then(async () => {
      const remaining = this.lastTurn + this.intervalMs - this.now();
      if (remaining > 0) await this.delay(remaining);
      // Usar el instante efectivo: una pausa larga no permite una ráfaga al despertar.
      this.lastTurn = this.now();
    });
    this.tail = turn.catch(() => {});
    return turn;
  }
}

const tenants = new Map<string, RequestPacer>();
const pages = new WeakMap<Page, RequestPacer>();

export async function installApiPacing(page: Page, tenant: { id: string; plan: 'FREE' | 'PRO' }): Promise<void> {
  if (pages.has(page)) return;
  let pacer = tenants.get(tenant.id);
  if (!pacer) {
    // FREE tiene 30 peticiones/minuto en application.yml. 2,1 s deja margen
    // y comparte presupuesto entre escenarios y contextos del mismo tenant.
    pacer = new RequestPacer(tenant.plan === 'FREE' ? 2_100 : 0);
    tenants.set(tenant.id, pacer);
  }
  pages.set(page, pacer);
  const origin = new URL(page.url()).origin;
  await page.route(url => url.origin === origin && url.pathname.startsWith('/api/v1/'), async route => {
    try {
      await waitForApiBudget(page);
      // Un goto o el cierre del contexto puede cancelar una petición de fondo
      // mientras espera su turno. No volver a enviar una petición cancelada.
      if (page.isClosed() || route.request().failure()) return;
      await route.fallback();
    } catch (error) {
      if (!page.isClosed() && !route.request().failure()) throw error;
    }
  });
}

export async function waitForApiBudget(page: Page): Promise<void> {
  await pages.get(page)?.wait();
}
