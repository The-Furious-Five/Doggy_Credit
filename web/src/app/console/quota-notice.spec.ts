import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { TenantService, UsoTenant } from '../api';
import { AppRole } from '../auth/permissions';
import { AuthSession, AuthSessionService } from '../auth/session.service';
import { DsToastService } from '../design-system';
import { QuotaNotice } from './quota-notice';
import { TenantUsageService } from './tenant-usage.service';

const usage = (evaluacionesMes: number, limiteMensual = 500): UsoTenant => ({
  evaluacionesMes,
  limiteMensual,
});
const admin = (roles: AppRole[] = ['TENANT_ADMIN']): AuthSession => ({
  isAuthenticated: true,
  name: 'Rosa',
  tenantId: 'tenant-1',
  roles,
});

describe('Avisos de cuota al 80 % y al 100 % (E-11)', () => {
  const api = { obtenerUsoTenant: vi.fn() };
  const session = signal(admin());

  beforeEach(() => {
    vi.resetAllMocks();
    sessionStorage.clear();
    session.set(admin());
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: TenantService, useValue: api },
        { provide: AuthSessionService, useValue: { session } },
      ],
    });
  });

  function render(response: Observable<UsoTenant>): ComponentFixture<QuotaNotice> {
    api.obtenerUsoTenant.mockReturnValue(response);
    const fixture = TestBed.createComponent(QuotaNotice);
    fixture.detectChanges();
    return fixture;
  }
  const page = (f: ComponentFixture<QuotaNotice>) => f.nativeElement as HTMLElement;
  const alert = (f: ComponentFixture<QuotaNotice>) => page(f).querySelector('ds-alert');

  it('no muestra nada mientras carga el uso', () => {
    const f = render(new Subject<UsoTenant>());
    expect(alert(f)).toBeNull();
    expect(page(f).textContent?.trim()).toBe('');
  });

  it('no muestra un aviso si el uso no se pudo consultar (la barra lateral ya lo indica)', () => {
    const f = render(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(alert(f)).toBeNull();
  });

  it.each([
    ['sin evaluaciones', usage(0)],
    ['por debajo del 80 %', usage(399)],
    ['plan sin límite', usage(4200, 0)],
  ])('no avisa %s', (_, value) => {
    expect(alert(render(of(value)))).toBeNull();
  });

  it('al 80 % avisa con tono de advertencia, sin interrumpir (role=status)', () => {
    const f = render(of(usage(400)));
    // Al entrar ya con el umbral superado basta el aviso: sin toast.
    expect(TestBed.inject(DsToastService).message()).toBe('');
    expect(alert(f)?.getAttribute('data-tone')).toBe('warning');
    expect(alert(f)?.getAttribute('role')).toBe('status');
    expect(page(f).textContent).toContain('Usaste el 80 % de las evaluaciones del mes');
    expect(page(f).textContent).toContain('Llevas 400 de 500');
  });

  it('entre el 80 % y el 100 % informa el porcentaje real, sin redondear hacia arriba', () => {
    expect(page(render(of(usage(499)))).textContent).toContain('Usaste el 99 %');
  });

  it('al 100 % avisa que se pausan las evaluaciones nuevas (role=alert)', () => {
    const f = render(of(usage(500)));
    expect(alert(f)?.getAttribute('data-tone')).toBe('danger');
    expect(alert(f)?.getAttribute('role')).toBe('alert');
    expect(page(f).textContent).toContain('Llegaste al límite de evaluaciones del mes');
    expect(page(f).textContent).toContain('Usaste 500 de 500');
  });

  it('a TENANT_ADMIN le enlaza su plan; a quien no puede abrir Configuración, no', () => {
    let f = render(of(usage(450)));
    expect(page(f).querySelector('a[href="/app/configuracion#plan"]')?.textContent?.trim()).toBe(
      'Ver tu plan',
    );

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: TenantService, useValue: api },
        { provide: AuthSessionService, useValue: { session } },
      ],
    });
    session.set(admin(['ANALYST']));
    f = render(of(usage(450)));
    expect(page(f).querySelector('a')).toBeNull();
    expect(page(f).textContent).toContain(
      'Avisa a quien administra tu institución para revisar el plan.',
    );
  });

  it('aparece al superar el umbral durante la sesión (p. ej. después de evaluar)', () => {
    const next = new Subject<UsoTenant>();
    api.obtenerUsoTenant.mockReturnValueOnce(of(usage(399))).mockReturnValueOnce(next);
    const f = TestBed.createComponent(QuotaNotice);
    f.detectChanges();
    expect(alert(f)).toBeNull();

    TestBed.inject(TenantUsageService).refresh();
    next.next(usage(400));
    f.detectChanges();
    expect(page(f).textContent).toContain('Usaste el 80 %');
    // El aviso puede quedar fuera de pantalla (p. ej. en el resultado): el toast lo anuncia.
    expect(TestBed.inject(DsToastService).message()).toBe(
      'Usaste el 80 % de las evaluaciones del mes.',
    );
  });

  it('notifica con toast cada subida de umbral, no las consultas dentro del mismo', () => {
    const responses = [new Subject<UsoTenant>(), new Subject<UsoTenant>()];
    api.obtenerUsoTenant
      .mockReturnValueOnce(of(usage(420)))
      .mockReturnValueOnce(responses[0])
      .mockReturnValueOnce(responses[1]);
    const toast = TestBed.inject(DsToastService);
    const quota = TestBed.inject(TenantUsageService);
    const f = TestBed.createComponent(QuotaNotice);
    f.detectChanges();

    quota.refresh();
    responses[0].next(usage(430));
    f.detectChanges();
    expect(toast.message()).toBe('');

    quota.refresh();
    responses[1].next(usage(500));
    f.detectChanges();
    expect(toast.message()).toBe('Llegaste al límite de evaluaciones del mes.');
  });

  it('ocultar un aviso dura la sesión, pero el siguiente umbral vuelve a avisar', () => {
    const next = new Subject<UsoTenant>();
    api.obtenerUsoTenant.mockReturnValueOnce(of(usage(420))).mockReturnValueOnce(next);
    const f = TestBed.createComponent(QuotaNotice);
    f.detectChanges();

    (page(f).querySelector('button') as HTMLButtonElement).click();
    f.detectChanges();
    expect(alert(f)).toBeNull();
    expect(sessionStorage.getItem('dc-aviso-cuota:tenant-1:warning')).toBe('1');

    // Otra vista de la misma sesión lo sigue respetando.
    const again = TestBed.createComponent(QuotaNotice);
    again.detectChanges();
    expect(alert(again)).toBeNull();

    TestBed.inject(TenantUsageService).refresh();
    next.next(usage(500));
    f.detectChanges();
    expect(page(f).textContent).toContain('Llegaste al límite');
  });

  it('sin sessionStorage disponible, ocultar funciona igual en la vista', () => {
    const blocked = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('bloqueado', 'SecurityError');
    });
    const f = render(of(usage(450)));
    (page(f).querySelector('button') as HTMLButtonElement).click();
    f.detectChanges();
    expect(alert(f)).toBeNull();
    blocked.mockRestore();
  });
});
