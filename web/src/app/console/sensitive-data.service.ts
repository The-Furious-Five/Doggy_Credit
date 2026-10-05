import { computed, inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { catchError, map, of } from 'rxjs';
import { TenantService } from '../api';

@Injectable({ providedIn: 'root' })
export class SensitiveDataService {
  private readonly tenantApi = inject(TenantService);
  private readonly oidc = inject(OidcSecurityService);
  private readonly preferences = toSignal(this.tenantApi.obtenerPreferenciasTenant().pipe(
    catchError(() => of({ enmascararDatos: true })),
  ), {
    initialValue: { enmascararDatos: true },
  });
  private readonly roles = toSignal(
    this.oidc.userData$.pipe(map(({ userData }) => userData?.realm_access?.roles ?? [] as string[])),
    { initialValue: [] as string[] },
  );
  readonly shouldMask = computed(() => this.preferences().enmascararDatos
    && !this.roles().some((role: string) => ['TENANT_ADMIN', 'PLATFORM_ADMIN'].includes(role)));

  document(value: string): string {
    if (!this.shouldMask()) return value;
    const visible = value.slice(-4);
    return `${'•'.repeat(Math.max(4, value.length - visible.length))}${visible}`;
  }

  amount(value: string): string {
    return this.shouldMask() ? '••••••' : value;
  }
}
