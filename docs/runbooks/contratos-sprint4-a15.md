# Contratos del Sprint 4 (A-15 / LOS-128)

Contract-first para ANA-01, ANA-02, TEN-04, ANA-04 e INT-07. La ruta nueva va
en un **tag propio** (`operador`), como `revision-manual` y `planes`: con
`skipDefaultInterface`, sumar la operación a `AdminApi` rompería la compilación
de `AdminController`. Así el backend compila igual y la ruta responde 404 hasta
que D-14 implemente `OperadorApi`. Todo campo nuevo de un esquema existente es
opcional.

| Requisito | Cambio | Implementa |
| --- | --- | --- |
| RF-ANA-01 | `UsoTenant` + `integracionesActivas`, `limiteIntegraciones` (0 = sin límite) en `GET /tenant/actual/uso` | B-12 (LOS-131) |
| RF-ANA-02 | `AnalyticsPro` + `distribucionBandas`, `tasaOverride`, `aceptacionPorProducto` | C-14 (LOS-130), D-13 (LOS-133) |
| RF-TEN-04 | `evaluacionesMes` y `limiteMensual` en `GET /admin/tenants`; `TENANT_SUSPENDED` (403) | Ya fusionado con E-14 (LOS-129) |
| RF-ANA-04 | `obtenerMetricasOperador`, `GET /operador/metricas?desde&hasta` (solo `PLATFORM_ADMIN`) | D-14 (LOS-134) |
| RF-INT-07 | `GET`/`PUT`/`DELETE /integraciones/{fuente}/credenciales` (tag `credenciales-integraciones`, solo `TENANT_ADMIN`) | Ya fusionado con E-15 (LOS-135) |

No hay códigos de error nuevos: `TENANT_SUSPENDED` llegó con E-14 y el resto usa
códigos existentes.

## ANA-02: definiciones de los KPIs de cartera

Se calculan sobre el mismo intervalo de `GET /analytics/resumen` (fechas en
America/La_Paz) y solo para los planes con analítica.

- **`distribucionBandas`**: siempre las cinco bandas A–E, con 0 si no hay casos.
  Base: las evaluaciones del intervalo con score (las `FALLIDA` no cuentan).
- **`tasaOverride`**: `overrides` = casos de revisión manual resueltos por una
  persona (`POST /revision-manual/{id}/resolucion`, aprobados o rechazados), por
  la fecha de la evaluación; `evaluaciones` = base del cálculo;
  `porcentaje` = overrides / evaluaciones × 100.
- **`aceptacionPorProducto`**: un elemento por tipo de producto. Recomendado = el
  primero del ranking C-06 es de ese tipo; aceptado = esa evaluación quedó
  `PREAPROBADO` o se aprobó en revisión manual con un producto del mismo tipo. No
  mide desembolsos.

Si C-14 necesita otra definición, se cambia aquí y en el contrato en el mismo PR.

## ANA-04: métricas del operador

Solo agregados: nada de nombres, NIT ni IDs de tenants. `tenantsPorPlan`,
`tenantsActivos` y `tenantsSuspendidos` son la foto al consultar;
`tenantsActivados`, `conversionesFreeAPro` (FREE → PRO en `audit.access_log`) y
`evaluaciones` se cuentan en el período (UTC, por defecto el mes en curso).
Para el consumo, ADR-019 ya ofrece `billing.OperatorUsageQuery`; un intervalo
que no sea un mes completo necesita su propio agregado con el mismo patrón.

## INT-07: credenciales propias

E-15 (LOS-135) fusionó el contrato junto con su implementación antes que este
PR, así que A-15 no lo redefine: el secreto (`clave`) es `writeOnly`, se cifra con
la DEK del tenant y la respuesta solo dice `configuradas` y `ultimosCuatro`. Ver
[credenciales-integraciones.md](credenciales-integraciones.md).

## Verificación de este PR

- `bash scripts/generate_clients.sh`: el contrato valida y el cliente Angular
  queda regenerado en `web/src/app/api/`.
- `./mvnw -o -q compile` en `backend/`: compila con `OperadorApi` generada y
  sin implementar.
- oasdiff v1.32.1 `breaking` contra `main`: sin cambios incompatibles
  (1 ruta nueva y 5 propiedades opcionales).
