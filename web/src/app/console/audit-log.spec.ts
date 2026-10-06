import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { AuditoriaService, PaginaAuditoria } from '../api';
import { AuthSessionService } from '../auth/session.service';
import { AppRole } from '../auth/permissions';
import { AuditLog } from './audit-log';

const page = (n: number, siguienteCursor?: number): PaginaAuditoria => ({
  items: Array.from({ length: n }, (_, i) => ({
    id: `id-${i}`,
    secuencia: 100 - i,
    actor: 'admin-demo',
    accion: 'ASSESSMENT_CREATED',
    tipoRecurso: 'CREDIT_ASSESSMENT',
    correlationId: `corr-${i}`,
    creadoEn: '2026-10-05T12:00:00Z',
    hash: 'abc',
  })),
  siguienteCursor,
});

describe('Bitácora de auditoría (C-08)', () => {
  const api = { listarAuditoria: vi.fn() };

  async function render(roles: AppRole[]): Promise<ComponentFixture<AuditLog>> {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuditoriaService, useValue: api },
        {
          provide: AuthSessionService,
          useValue: { session: signal({ isAuthenticated: true, name: 'Ana', tenantId: 't-1', roles }) },
        },
      ],
    });
    const fixture = TestBed.createComponent(AuditLog);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  async function settle(f: ComponentFixture<AuditLog>): Promise<void> {
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
  }
  const text = (f: ComponentFixture<AuditLog>) => (f.nativeElement as HTMLElement).textContent ?? '';
  const button = (f: ComponentFixture<AuditLog>, label: string) =>
    [...(f.nativeElement as HTMLElement).querySelectorAll('button')].find((b) => b.textContent?.includes(label))!;

  beforeEach(() => api.listarAuditoria.mockReset());

  it('quien no es TENANT_ADMIN no ve la bitácora ni se consulta la API', async () => {
    const f = await render(['ANALYST']);
    expect(text(f)).not.toContain('Bitácora de auditoría');
    expect(api.listarAuditoria).not.toHaveBeenCalled();
  });

  it('el administrador ve los registros, del más reciente al más antiguo', async () => {
    api.listarAuditoria.mockReturnValue(of(page(2)));
    const f = await render(['TENANT_ADMIN']);
    expect(f.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
    expect(text(f)).toContain('ASSESSMENT_CREATED');
    expect(text(f)).toContain('corr-0');
    expect(api.listarAuditoria).toHaveBeenCalledWith(undefined, undefined, undefined, 20);
  });

  it('muestra un estado vacío si no hay registros', async () => {
    api.listarAuditoria.mockReturnValue(of(page(0)));
    const f = await render(['TENANT_ADMIN']);
    expect(text(f)).toContain('Sin registros');
  });

  it('muestra un error con reintento si la API falla', async () => {
    api.listarAuditoria.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const f = await render(['TENANT_ADMIN']);
    expect(text(f)).toContain('No pudimos cargar la bitácora');

    api.listarAuditoria.mockReturnValue(of(page(1)));
    button(f, 'Reintentar').click();
    await settle(f);
    expect(f.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('si el backend responde 403 explica que falta el rol', async () => {
    api.listarAuditoria.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const f = await render(['TENANT_ADMIN']);
    expect(text(f)).toContain('Solo el administrador del tenant puede ver la bitácora');
  });

  it('filtra por fechas: desde la medianoche del día inicial hasta el día siguiente al final', async () => {
    api.listarAuditoria.mockReturnValue(of(page(1)));
    const f = await render(['TENANT_ADMIN']);
    f.componentInstance.fromDraft = '2026-10-01';
    f.componentInstance.toDraft = '2026-10-05';
    f.componentInstance.applyFilter();
    await settle(f);

    expect(api.listarAuditoria).toHaveBeenLastCalledWith(
      new Date(2026, 9, 1).toISOString(),
      new Date(2026, 9, 6).toISOString(),
      undefined,
      20,
    );
  });

  it('navega con el cursor hacia registros más antiguos y vuelve a los más recientes', async () => {
    api.listarAuditoria.mockReturnValue(of(page(20, 81)));
    const f = await render(['TENANT_ADMIN']);
    expect(button(f, 'Más recientes').disabled).toBe(true);

    api.listarAuditoria.mockReturnValue(of(page(5)));
    button(f, 'Más antiguos').click();
    await settle(f);
    expect(api.listarAuditoria).toHaveBeenLastCalledWith(undefined, undefined, 81, 20);
    expect(button(f, 'Más antiguos').disabled).toBe(true);

    api.listarAuditoria.mockReturnValue(of(page(20, 81)));
    button(f, 'Más recientes').click();
    await settle(f);
    expect(api.listarAuditoria).toHaveBeenLastCalledWith(undefined, undefined, undefined, 20);
    expect(button(f, 'Más recientes').disabled).toBe(true);
  });
});
