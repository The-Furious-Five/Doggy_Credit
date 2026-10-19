# ADR-019 · Consumo entre tenants con una función de lectura bajo RLS

Fecha: 2026-10-18. Estado: aceptado para E-14 (LOS-129, RF-TEN-04).

## Contexto

El operador necesita el consumo mensual de todos los tenants, incluidos los
suspendidos y los que no tienen consumo. `billing.monthly_usage` tiene RLS
habilitado y forzado. La conexión de aplicación usa `doggycredit_app`, sin
superusuario ni `BYPASSRLS`; consultar la tabla con el tenant del operador
solo muestra su propio consumo. D-14 necesita reutilizar la lectura de estos
contadores sin abrir acceso a evaluaciones o datos personales.

## Decisión

La migración `V2026_10_18_1000__billing_operator_monthly_usage.sql` crea:

- `doggycredit_usage_reader`: rol `NOLOGIN`, `NOINHERIT`, `NOBYPASSRLS`, sin
  superusuario, miembros, creación de roles o bases ni permisos de escritura.
  Solo tiene `USAGE` del esquema y `SELECT` sobre las tres columnas del contador.
- Una política adicional `FOR SELECT TO doggycredit_usage_reader USING (true)`.
  La tabla conserva `ENABLE/FORCE ROW LEVEL SECURITY` y `tenant_isolation`.
  La política no concede acceso a `doggycredit_app`.
- `billing.operator_monthly_usage(text)`: función `STABLE SECURITY DEFINER`,
  propiedad del lector, con `search_path = pg_catalog`, nombres de tabla
  cualificados y sin SQL dinámico. Recibe un mes y solo devuelve `tenant_id`
  y `evaluaciones`. Se revoca la ejecución de `PUBLIC`; la app recibe `EXECUTE`.
  El permiso temporal `CREATE` del lector, necesario para transferir la
  propiedad de la función, se revoca en la misma migración.

El puerto público `billing.OperatorUsageQuery.evaluationsForMonth(YearMonth)`
exige `PLATFORM_ADMIN` y ejecuta una única consulta de lectura. El controlador
también exige ese rol. El listado completa con cero los tenants sin contador
y obtiene el límite de su plan actual desde `PlanCatalog`; el mes es UTC,
como en `UsageMeter`. D-14 puede reutilizar el puerto para contadores mensuales;
un intervalo parcial de mes requiere su propio agregado con el mismo patrón,
porque el contador mensual no permite reconstruir los días individuales.

La ejecución de la función es una capacidad interna de la app: un cliente HTTP
no ejecuta SQL ni elige el rol de PostgreSQL. La autorización del operador se
verifica en Java; la función limita la información disponible incluso si una
consulta interna la invoca con otro contexto de tenant. No se confía en un GUC
de rol enviado por el cliente para decidir si se permite saltar el aislamiento.

## Alternativas consideradas

- Desactivar RLS o dar `BYPASSRLS`/superusuario a la app: descartado; concedería
  acceso general a datos entre tenants.
- `SECURITY DEFINER` propiedad del administrador de migraciones: descartado;
  sus privilegios exceden lo que necesita una lectura de contadores.
- Cambiar `TenantContext` y abrir una transacción por tenant: respeta RLS,
  pero exige N consultas y cambios de contexto para el listado.
- Replicar contadores en otra tabla: añade sincronización y riesgo de consumo
  desactualizado sin necesidad en este MVP.

## Consecuencias

La aplicación mantiene el aislamiento de lecturas y escrituras directas de
`monthly_usage`. El rol lector tampoco puede modificar datos o convertirse en
rol de sesión de la app. No se agregan credenciales ni conexiones privilegiadas
al proceso. La migración requiere el administrador que ya usa Flyway para
crear roles y políticas; el rol lector no debe recibir miembros ni permisos
adicionales durante la operación.

Las pruebas con PostgreSQL 17 real verifican los contadores, el propietario y
los permisos de la función, ausencia de ejecución pública, la prohibición de
`SET ROLE`, la persistencia de RLS forzado y la imposibilidad de leer o escribir
filas ajenas directamente. El metatest existente sigue exigiendo
`tenant_isolation` en toda tabla con `tenant_id`.
