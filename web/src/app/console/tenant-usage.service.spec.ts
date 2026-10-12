import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { TenantService, UsoTenant } from '../api';
import { TenantUsageService, quotaLevel } from './tenant-usage.service';

const usage = (evaluacionesMes: number, limiteMensual: number): UsoTenant => ({
  evaluacionesMes,
  limiteMensual,
});

describe('Umbrales de la cuota mensual (E-11)', () => {
  it.each([
    [usage(0, 500), 'ok'],
    [usage(399, 500), 'ok'],
    [usage(400, 500), 'warning'],
    [usage(499, 500), 'warning'],
    [usage(500, 500), 'exhausted'],
    // Un plan rebajado puede dejar el consumo por encima del nuevo límite.
    [usage(620, 500), 'exhausted'],
    // 80 % de 3 es 2,4: con 2 todavía no se avisa; con 3 se llegó al límite.
    [usage(2, 3), 'ok'],
    [usage(3, 3), 'exhausted'],
    // 0 significa sin límite mensual.
    [usage(9000, 0), 'ok'],
  ] as const)('%o → %s', (value, level) => {
    expect(quotaLevel(value)).toBe(level);
  });
});

describe('Uso mensual compartido (E-11)', () => {
  const api = { obtenerUsoTenant: vi.fn() };

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.configureTestingModule({ providers: [{ provide: TenantService, useValue: api }] });
  });

  it('distingue la carga (undefined) de un error (null), sin inventar un nivel', () => {
    const pending = new Subject<UsoTenant>();
    api.obtenerUsoTenant.mockReturnValue(pending);
    const service = TestBed.inject(TenantUsageService);
    expect(service.usage()).toBeUndefined();
    expect(service.level()).toBeNull();

    pending.error(new HttpErrorResponse({ status: 500 }));
    expect(service.usage()).toBeNull();
    expect(service.level()).toBeNull();
  });

  it('refresh vuelve a consultar y conserva el último valor mientras llega el nuevo', () => {
    const next = new Subject<UsoTenant>();
    api.obtenerUsoTenant.mockReturnValueOnce(of(usage(399, 500))).mockReturnValueOnce(next);
    const service = TestBed.inject(TenantUsageService);
    expect(service.level()).toBe('ok');

    service.refresh();
    expect(api.obtenerUsoTenant).toHaveBeenCalledTimes(2);
    expect(service.usage()).toEqual(usage(399, 500));

    next.next(usage(400, 500));
    expect(service.level()).toBe('warning');
  });

  it('un error al refrescar no impide consultar de nuevo', () => {
    api.obtenerUsoTenant
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })))
      .mockReturnValueOnce(of(usage(500, 500)));
    const service = TestBed.inject(TenantUsageService);
    expect(service.usage()).toBeNull();

    service.refresh();
    expect(service.level()).toBe('exhausted');
  });
});
