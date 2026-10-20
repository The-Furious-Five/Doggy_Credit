# ADR-023 · Los entitlements se leen del servidor, no del token

Fecha: 2026-10-20. Estado: aceptado (A-02, A-11; construido).

## Contexto

El JWT de Keycloak lleva `tenant_id` y roles (mapper en
`infra/keycloak/doggycredit-realm.json`). Si también llevara el plan o sus
límites, un cambio de plan solo se notaría al renovar el token.

## Decisión

El plan no viaja en el token. `billing.Entitlements.of(tenantId)`
(`EntitlementsImpl`) lee en cada llamada el plan de `tenancy.tenants` (vía
`TenantPlans`) y los límites y funciones de `billing.plans` (`PlanCatalog`).
Las funciones por plan se aplican con esa lectura (p. ej. `403
FEATURE_NOT_IN_PLAN` en webhooks). El limitador de tasa vuelve a leer el plan
cada minuto (`TenantRateLimiter`). Un cambio de plan se nota en ≤ 1 min sin
reiniciar sesión (comprobado en E2E, `docs/runbooks/e2e-flujos-sprint3.md`).

## Alternativas consideradas

- Plan como claim del token: descartado; queda obsoleto hasta que expire.
- Caché larga en la API: descartado; contradice el objetivo de ≤ 1 min.
- Revocar tokens al cambiar de plan: descartado; complejidad en Keycloak sin beneficio.

## Consecuencias

Una lectura de base por solicitud protegida, aceptable al volumen actual; si pesa,
una caché con TTL de segundos mantiene el requisito. El token queda mínimo y no
puede falsear funciones del plan. Web y apps móviles obtienen el mismo comportamiento.
