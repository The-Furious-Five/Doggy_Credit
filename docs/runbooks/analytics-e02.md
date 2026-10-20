# E-02 · KPIs por tenant

`/app/resumen` y `/app/analitica` consumen el cliente OpenAPI generado para
`GET /api/v1/analytics/resumen?desde=2026-10-01&hasta=2026-10-31`.
Comparten el componente `AnalyticsDashboard`, filtros del Design System y
estados de carga, error con reintento y ausencia de datos.

## Contrato y métricas

El tenant procede del JWT validado, a través de `TenantContext`; el plan se lee
de `TenantRegistry` y `PlanCatalog`. Parámetros o claims de plan enviados por
el cliente no conceden funciones. Se responde con `Cache-Control: no-store`.

Las fechas son inclusivas en `America/La_Paz`. SQL usa un intervalo
`[desde a las 00:00, día posterior a hasta a las 00:00)` para incluir todo el
último día y aprovechar el índice `(tenant_id, created_at)`. Sin parámetros
se consulta desde el primer día del mes hasta hoy; si solo se envía `hasta`,
se usa el primer día de ese mes. Un intervalo invertido o una fecha inválida
responde 400 RFC 9457 con `VALIDATION_ERROR` y `correlationId`.

| Indicador | Definición |
| --- | --- |
| Evaluaciones | Cantidad de evaluaciones persistidas en el intervalo; las repeticiones idempotentes conservan una sola fila. |
| Score promedio | Promedio del score, escala 0–1000; `null` sin evaluaciones. |
| Integraciones activas | Estado actual: buró obligatorio más banco y billetera activos. No es un histórico del intervalo. |
| Con recomendación | Evaluaciones con primer producto recomendado / total de evaluaciones × 100. |
| Necesidades | Cantidad por categoría de D-06 y porcentaje del total evaluado. |
| Conversión | Evaluaciones `PREAPROBADO` / total × 100, según la definición confirmada para E-02. |
| Tiempo de decisión | Mediana de los milisegundos de procesamiento, desde la comprobación de cuota hasta la captura de la recomendación, antes de persistir. `null` sin muestras. |
| Monto recomendado | Suma del primer producto recomendado por evaluación, en la moneda del tenant. El contrato usa un string decimal para los montos. |
| Recomendaciones por producto | Cantidad, porcentaje de las evaluaciones con recomendación y monto por tipo de producto. |
| Modelos | Último resultado semanal por modelo de D-08: PSI, estado, ventana y tamaños de muestras. La ventana de D-08 se muestra explícitamente y no depende del filtro de evaluaciones. |
| Distribución de bandas (C-14) | Cantidad y porcentaje por banda, siempre de A a E y con 0 si no hay casos. Base: evaluaciones del intervalo con decisión, es decir, sin `FALLIDA` ni `PENDIENTE`. |
| Tasa de override (C-14) | Casos de la bandeja resueltos por una persona (`APROBADO` o `RECHAZADO`), contados por la fecha de la evaluación, no de la resolución / la misma base de bandas × 100. |
| Aceptación por producto (C-14) | Por tipo (`MICROCREDITO`, `VERDE`, `PYME`): recomendadas = el primer producto recomendado es de ese tipo; aceptadas = de esas, las que quedaron `PREAPROBADO` o se aprobaron en la bandeja con un producto del mismo tipo; % = aceptadas / recomendadas × 100. No mide desembolsos. |

Los porcentajes se redondean a dos decimales. Sin evaluaciones, los conteos,
porcentajes y montos son cero; los promedios y duraciones desconocidos son
`null`. La interfaz muestra `—` para esos valores y respeta el enmascaramiento
de montos existente.

Los cinco indicadores básicos están disponibles en todos los planes. El
objeto `pro` solo se consulta y serializa si `PlanInfo.analytics` es verdadero:
el catálogo actual habilita Pro y Enterprise. Gratis no recibe ese objeto ni
consulta las métricas avanzadas o D-08. No hay valores avanzados de ejemplo
en la interfaz.

## Persistencia y aislamiento

Flyway aplica `V2026_10_06_1200__analytics_summary_views.sql`. Agrega al resultado
inmutable de la evaluación su necesidad, primer producto recomendado, monto
y duración; publica `assessment.analytics_evaluations_v1` e
`integration.analytics_active_v1` con `security_invoker = true`.

La migración se ordena después de las políticas y del listado de evaluaciones
incorporados a `main` el 6 de octubre. Reutiliza el índice de C-09
`(tenant_id, created_at DESC, id)` para evitar un choque de nombres y un índice
duplicado. Resumen conserva también las evaluaciones recientes de C-09.

El consumidor agrega mediante SQL y filtra explícitamente por tenant y
fecha; RLS también protege las consultas directas a las vistas sin ese filtro.
Sin `TenantContext` las vistas no devuelven filas. Una transacción de lectura
`REPEATABLE_READ` mantiene consistentes las distintas partes del resumen.

Las nuevas evaluaciones capturan D-06 y C-03 mediante sus APIs públicas. La
selección usa el catálogo activo del tenant, moneda, banda, tipo de titular,
destino, monto, tasa, capacidad de pago y plazo; conserva el ranking del motor
existente. Con fuentes fallidas no guarda una recomendación. La captura no
cambia el estado de aprobación del flujo existente y no se repite cuando se
reutiliza una clave idempotente.

Las evaluaciones anteriores a la migración mantienen `null` en las nuevas
dimensiones. Se incluyen en el conteo y promedio, pero no se les asigna una
necesidad, recomendación o tiempo reconstruidos con reglas posteriores.

## Verificación reproducible

Con Docker disponible, desde `backend/`:

```sh
./mvnw verify
```

`AnalyticsControllerTests` verifica los planes FREE/PRO/ENTERPRISE, manipulación
de tenant y plan, RLS sin predicados, ausencia de contexto, fechas en Bolivia,
vacíos, autenticación y errores RFC 9457. `ProductRecommendationServiceTests`
verifica la selección y el aislamiento del catálogo; `AssessmentControllerTests`
verifica que el snapshot se persista una vez con la idempotencia existente.

La prueba de rendimiento levanta PostgreSQL 17 y el servidor HTTP en un puerto
aleatorio. Lee las 50 personas de `seed/personas-sinteticas.json` y prepara
30 evaluaciones sintéticas por persona, 1.500 en total, más integraciones y un
resultado D-08. Realiza diez peticiones HTTP completas, incluida la primera,
y exige menos de 1.000 ms en cada una. El decoder JWT usa tokens de prueba;
los filtros, la resolución de plan, consultas SQL, RLS, serialización y red
local son reales. El log `E-02 rendimiento` contiene cada medición.

Verificación del 5 de octubre de 2026: `./mvnw verify` pasó con 266 pruebas,
cero fallos, errores o pruebas omitidas. En la ejecución completa, las diez
peticiones tardaron `[35, 5, 5, 6, 6, 6, 5, 4, 5, 5]` ms: máximo 35 ms.

Desde `web/`:

```sh
npm run ds:check
npm run ds:test
NG_BUILD_CACHE_STORE=sqlite npm test -- --watch=false
NG_BUILD_CACHE_STORE=sqlite npm run build
```

El uso de SQLite evita el fallo de la caché LMDB en el entorno macOS descrito
en el runbook del sprint. Las pruebas de interfaz cubren ambas vistas, planes,
filtros, carga, reintento, valores desconocidos, D-08 y enmascaramiento. Las
pruebas de los interceptores incluyen el nuevo cliente AnalyticsService.

Verificación de interfaz: 159 pruebas aprobadas, las 8 pruebas del checker
aprobadas, `ds:check` conforme y compilación de producción exitosa sin avisos
de presupuesto (498,27 kB iniciales).

## Integración con main · 6 de octubre de 2026

Se integró E-02 con `origin/main` (`524d8a3`), conservando el motor de políticas,
el listado e historial de evaluaciones, las vistas de productos y la nueva
evaluación. Resumen combina los KPIs con la lista real de evaluaciones recientes;
el cliente Angular se regeneró desde el contrato que contiene todos los endpoints.

La migración de E-02, aún sin publicar, se ordenó después de las migraciones
de `main` y reutiliza su índice. Ninguna migración publicada de `origin/main`
se modificó. `AnalyticsMigrationUpgradeTests` crea el esquema previo de main,
inserta una evaluación y una política y aplica E-02: verifica que se conservan
datos e índice y que la vista sigue filtrando por tenant usando el login real
del rol restringido de PostgreSQL.

Verificación de la integración:

- `./mvnw clean verify`: 305 pruebas, cero fallos, errores o pruebas omitidas.
- Frontend: 199 pruebas; comprobación de KPIs y evaluaciones recientes juntos.
- `ds:check` y las 8 pruebas de `ds:test`: aprobados.
- Compilación de producción: 498,07 kB iniciales, sin avisos de presupuesto.
- Rendimiento HTTP con 1.500 evaluaciones: `[38, 6, 6, 6, 5, 5, 5, 4, 5, 5]` ms.

Si cambia el contrato, regenerar el cliente; no editar `web/src/app/api/`:

```sh
bash scripts/generate_clients.sh
```

## C-14 · KPIs de cartera (LOS-130, RF-ANA-02)

Llena `distribucionBandas`, `tasaOverride` y `aceptacionPorProducto` del objeto
`pro`, que A-15 (LOS-128) ya había publicado en el contrato. No cambia el
contrato ni el cliente generado. Igual que el resto de `pro`, un plan sin
analítica no recibe estos campos ni ejecuta sus consultas.

**Base de bandas y override:** evaluaciones del intervalo sin `FALLIDA` ni
`PENDIENTE` (decisión del 19 de octubre de 2026). Coincide con «con decisión»
del contrato. `PENDIENTE` existe en el enum, pero la aplicación no la persiste,
así que hoy esta base da lo mismo que «con score».

**Persistencia** (`V2026_10_19_1000__analytics_portfolio_kpis.sql`):

- `assessment.analytics_evaluations_v1` suma la columna `band` al final y repite
  `security_invoker = true`.
- `assessment.manual_review_resolutions.product_type`: al aprobar, la bandeja
  guarda el tipo del producto elegido como snapshot, igual que
  `recommended_product_type`. Lo obtiene de la Api pública
  `ProductCatalog.activeProductType`, sin JOIN con el esquema `recommendation`.
  Un `CHECK` impide guardar un tipo en un rechazo.
- `assessment.analytics_resolutions_v1` (`security_invoker = true`): cada
  resolución con la fecha, el estado y el producto recomendado de su evaluación.
  El JOIN queda dentro del módulo `assessment`.

**Resoluciones anteriores a C-14:** quedan con `product_type` en `null`. No se
reconstruyen con el catálogo actual, porque el producto pudo cambiar y habría
que cruzar esquemas. Cuentan como override, pero no como aceptadas. Afecta solo
a las resoluciones entre el 12/10 (C-10) y este cambio.

**Verificación (19 de octubre de 2026):**

- `AnalyticsControllerTests.calculaBandasOverrideYAceptacionPorProducto`: nueve
  evaluaciones sintéticas con todas las cifras calculables a mano (una aceptada
  con el mismo tipo, otra aprobada con otro tipo, un rechazo, una resolución sin
  tipo, una `FALLIDA` y una fuera del intervalo).
- FREE no recibe los campos nuevos; con cero evaluaciones salen las cinco bandas
  y los tres tipos en 0, sin dividir por cero; los tenants no ven las
  resoluciones de otros, ni siquiera con la vista sin predicado.
- `ManualReviewTests.aprobarGuardaElTipoDelProductoYRechazarLoDejaVacio`.
- Prueba al revés: contar las `FALLIDA`, aceptar con otro tipo y correr el
  intervalo del override hacen caer el test.
- Rendimiento con 1.500 evaluaciones: `[361, 84, 82, 64, 61, 87, 81, 85, 76, 99]` ms.
- `./mvnw clean verify`: 497 pruebas, cero fallos, errores o pruebas omitidas.
