/** Roles del realm, definidos en seed/tenants.yaml. */
export type AppRole = 'TENANT_ADMIN' | 'ANALYST' | 'API_CLIENT' | 'PLATFORM_ADMIN';

/** Una sola política para las rutas y todos los enlaces de la consola. */
export const APP_ROUTE_ROLES = {
  '/app/politicas': 'TENANT_ADMIN',
  '/app/productos': 'TENANT_ADMIN',
  '/app/configuracion': 'TENANT_ADMIN',
  '/app/desarrolladores': 'TENANT_ADMIN',
  '/app/admin/tenants': 'PLATFORM_ADMIN',
} as const satisfies Readonly<Record<string, AppRole>>;

export function isAppRole(value: unknown): value is AppRole {
  return (
    value === 'TENANT_ADMIN' ||
    value === 'ANALYST' ||
    value === 'API_CLIENT' ||
    value === 'PLATFORM_ADMIN'
  );
}

export function canAccessAppRoute(path: string, roles: readonly AppRole[]): boolean {
  const requiredRole: AppRole | undefined = APP_ROUTE_ROLES[path as keyof typeof APP_ROUTE_ROLES];
  return !requiredRole || roles.includes(requiredRole);
}
