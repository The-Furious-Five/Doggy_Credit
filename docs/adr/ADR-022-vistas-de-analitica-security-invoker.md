# ADR-022 · Vistas de analítica con `security_invoker`

Fecha: 2026-10-20. Estado: aceptado (E-02; construido).

## Contexto

Analítica agrega datos de `assessment` e `integration` sin acceder a sus tablas
ni a sus tipos internos (Spring Modulith). Una vista PostgreSQL normal se ejecuta
con los privilegios de su propietario y por tanto no aplica el RLS del invocador.

## Decisión

`V2026_10_06_1200__analytics_summary_views.sql` publica vistas versionadas
(`assessment.analytics_evaluations_v1`, `integration.analytics_active_v1`) con
`WITH (security_invoker = true)`: se evalúan con los privilegios de
`doggycredit_app`, así RLS sigue aplicando aunque una consulta olvide el
`WHERE tenant_id`. El consumidor además filtra explícitamente el tenant
autenticado y el intervalo. Las columnas nuevas de `assessment.credit_assessments`
son un snapshot inmutable de las dimensiones de analítica (los registros previos
quedan en `NULL`).

## Alternativas consideradas

- Vistas propiedad del administrador (comportamiento por defecto): descartado; saltarían RLS.
- Consultar las tablas de otro módulo directamente: descartado; rompe los límites de módulo.
- Tablas de resumen replicadas: descartado; sincronización y datos desactualizados.

## Consecuencias

El aislamiento por tenant se conserva en la base de datos. Las vistas son un
contrato entre módulos: un cambio incompatible implica una versión nueva (`_v2`).
Requiere PostgreSQL 15 o superior (el compose usa `postgres:17-alpine`).
