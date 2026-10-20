# ADR-029 · Métricas del operador con historial de planes y lectura bajo RLS

Fecha: 2026-10-20. Estado: aceptado (D-14, LOS-134, RF-ANA-04; construido).

## Contexto

`GET /operador/metricas` (contrato de A-15) da al `PLATFORM_ADMIN` cuatro cifras de
negocio: tenants por plan y suspendidos, activación, conversión y consumo. Dos datos
no estaban disponibles:

- **Conversión.** `audit.access_log` registra `PLAN_UPGRADED` y `PLAN_DOWNGRADED`,
  pero no desde qué plan ni hacia cuál, así que no distingue FREE → PRO de
  PRO → ENTERPRISE.
- **Activación y consumo de un período arbitrario.** `billing.OperatorUsageQuery`
  (ADR-019) solo tiene contadores por mes completo y no permite reconstruir días.
  Las evaluaciones están en `assessment.credit_assessments`, con RLS forzado: la
  conexión de la app solo ve las del tenant del operador.

## Decisión

1. **Historial de planes de solo inserción.** `tenancy.plan_changes`
   (`V2026_10_20_1200`) guarda `plan_anterior`, `plan_nuevo` y `cambiado_en`.
   `PlanChangeService` inserta una fila en cada subida y cada bajada efectiva, en
   la misma transacción que el cambio y a nombre del tenant afectado, como su
   bitácora. Tiene RLS habilitado y forzado con `tenant_isolation`; la app solo
   recibe `SELECT, INSERT` (DEBT-017), y un trigger rechaza `UPDATE`, `DELETE` y
   `TRUNCATE` con cualquier rol, como en `audit.access_log`.
2. **Lectura entre tenants con el patrón de ADR-019** (`V2026_10_20_1210`). Se crea
   el rol `doggycredit_metrics_reader` (`NOLOGIN`, `NOINHERIT`, `NOBYPASSRLS`, sin
   miembros), con `SELECT` solo sobre las columnas que cuenta:
   `credit_assessments(tenant_id, created_at)` y
   `plan_changes(plan_anterior, plan_nuevo, cambiado_en)`. Una política
   `FOR SELECT TO doggycredit_metrics_reader USING (true)` en cada tabla. Dos
   funciones `STABLE SECURITY DEFINER` de su propiedad, con
   `search_path = pg_catalog` y sin SQL dinámico:
   - `assessment.operator_evaluations(desde, hasta)`: evaluaciones por tenant en
     `[desde, hasta)`. El id sale de la base pero no de la API: la app lo cruza con
     el plan actual y lo descarta.
   - `tenancy.operator_plan_changes(desde, hasta)`: cambios agrupados por origen y
     destino, sin tenants.

   Se revoca la ejecución de `PUBLIC` y la app recibe `EXECUTE`. El `CREATE`
   temporal del lector para transferir la propiedad se revoca en la misma migración.
3. **Definiciones**, en UTC con fechas inclusivas y por defecto el mes calendario en
   curso. Activado: un tenant con al menos una evaluación en el período. Conversión:
   una subida desde FREE a PRO o ENTERPRISE en el período; el campo del contrato se
   sigue llamando `conversionesFreeAPro` por compatibilidad. Consumo: las
   evaluaciones del período, en total y por el plan que el tenant tiene **hoy**
   (`TenantsPorPlan.evaluaciones`).
4. **Ubicación.** El controlador y el servicio viven en `analytics`, que ya depende
   de `tenancy` y no tiene dependientes, así que no se crea ningún ciclo de módulos.
   El servicio exige `PLATFORM_ADMIN` además del controlador, como
   `OperatorUsageQuery`.

## Alternativas consideradas

- **Leer el plan anterior de `audit.access_log`.** Habría que agregar el detalle a la
  cadena de hash y reinterpretar filas antiguas. Descartado: la bitácora es
  evidencia, no un modelo de consulta.
- **Reutilizar `billing.monthly_usage`.** Solo sirve para meses completos (ADR-019) y
  cuenta lo cobrado, no las evaluaciones creadas en un rango de días.
- **Un solo lector para ADR-019 y ADR-029.** Juntaría permisos de dos módulos en un
  rol. Un rol por función de lectura deja cada permiso acotado y auditable.
- **Una consulta por tenant cambiando `TenantContext`.** Respeta RLS, pero son N
  consultas y cambios de contexto por cada carga de la vista.

## Consecuencias

- Las conversiones se cuentan desde que esta migración está en producción. Los
  cambios de plan anteriores solo están en `audit.access_log`, sin origen ni destino,
  y no se reconstruyen.
- Las tablas conservan RLS forzado. Ni la app ni el lector pueden leer o escribir
  filas ajenas directamente, y el lector no puede leer `score`, razones ni datos del
  titular.
- La respuesta solo tiene conteos: ningún nombre, NIT ni id de tenant (RF-SEG-07).
- `OperatorMetricsTests` (PostgreSQL 17 real) verifica cada cifra con datos en un
  período aislado, los bordes UTC del intervalo, el 403 de `ANALYST`, `TENANT_ADMIN`
  y `API_CLIENT`, y las propiedades del rol y de las funciones. `PlanChangeTests`
  verifica que la subida y la bajada quedan en el historial.
