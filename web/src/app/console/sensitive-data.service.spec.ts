import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TenantService } from '../api';
import { AppRole } from '../auth/permissions';
import { AuthSessionService } from '../auth/session.service';
import { SensitiveDataService } from './sensitive-data.service';

describe('Permisos de datos sensibles', () => {
  const roles = signal<AppRole[]>(['ANALYST']);
  const preferences = { obtenerPreferenciasTenant: vi.fn() };

  beforeEach(() => {
    roles.set(['ANALYST']);
    preferences.obtenerPreferenciasTenant.mockReturnValue(of({ enmascararDatos: true }));
    TestBed.configureTestingModule({
      providers: [
        { provide: TenantService, useValue: preferences },
        { provide: AuthSessionService, useValue: { session: () => ({ roles: roles() }) } },
      ],
    });
  });

  it('enmascara al analista y utiliza los roles de la sesión para revelar los datos', () => {
    const service = TestBed.inject(SensitiveDataService);
    expect(service.document('CI-PRUEBA-123456')).toMatch(/^•{12}3456$/);
    expect(service.amount('2500.00')).toBe('••••••');
    roles.set(['TENANT_ADMIN']);
    expect(service.document('CI-PRUEBA-123456')).toBe('CI-PRUEBA-123456');
    roles.set(['PLATFORM_ADMIN']);
    expect(service.amount('2500.00')).toBe('2500.00');
    roles.set([]);
    expect(service.amount('2500.00')).toBe('••••••');
  });

  it('conserva el enmascaramiento si no se pueden cargar las preferencias', () => {
    preferences.obtenerPreferenciasTenant.mockReturnValue(throwError(() => new Error('offline')));
    expect(TestBed.inject(SensitiveDataService).shouldMask()).toBe(true);
  });

  it('respeta una preferencia de tenant que permite ver los datos', () => {
    preferences.obtenerPreferenciasTenant.mockReturnValue(of({ enmascararDatos: false }));
    expect(TestBed.inject(SensitiveDataService).amount('2500.00')).toBe('2500.00');
  });
});
