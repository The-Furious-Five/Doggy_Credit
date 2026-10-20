import { Injectable, signal } from '@angular/core';

/** Se activa cuando la API responde TENANT_SUSPENDED; la consola lo muestra como aviso fijo. */
@Injectable({ providedIn: 'root' })
export class TenantSuspensionService {
  readonly suspended = signal(false);
}
