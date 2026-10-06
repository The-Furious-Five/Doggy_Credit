import { computed, inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { TenantService } from '../api';
import { AuthSessionService } from '../auth/session.service';

@Injectable({ providedIn: 'root' })
export class SensitiveDataService {
  private readonly tenantApi = inject(TenantService);
  private readonly auth = inject(AuthSessionService);
  private readonly preferences = toSignal(
    this.tenantApi
      .obtenerPreferenciasTenant()
      .pipe(catchError(() => of({ enmascararDatos: true }))),
    {
      initialValue: { enmascararDatos: true },
    },
  );
  readonly shouldMask = computed(
    () =>
      this.preferences().enmascararDatos &&
      !this.auth
        .session()
        .roles.some((role) => role === 'TENANT_ADMIN' || role === 'PLATFORM_ADMIN'),
  );

  document(value: string): string {
    if (!this.shouldMask()) return value;
    const visible = value.slice(-4);
    return `${'•'.repeat(Math.max(4, value.length - visible.length))}${visible}`;
  }

  amount(value: string): string {
    return this.shouldMask() ? '••••••' : value;
  }
}
