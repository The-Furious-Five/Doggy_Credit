# Productos elegibles y ranking de recomendaciones (E-16)

LOS-139 implementa RF-REC-02 (todos los planes) y RF-REC-03 (PRO/ENTERPRISE).
Prerrequisitos integrados en orden: [C-06, PR #43](https://github.com/The-Furious-Five/Doggy_Credit/pull/43)
y [contrato E-16, PR #66](https://github.com/The-Furious-Five/Doggy_Credit/pull/66).
Los tipos y la semántica de los campos se documentan en el
[runbook del contrato](contratos-recomendaciones-e16.md).

## Resultado por plan

`POST /credit-assessments` y `GET /credit-assessments/{id}` devuelven el mismo
snapshot de elegibilidad y, cuando corresponde, de recomendaciones. La pestaña
del perfil de C-06 obtiene la evaluación del listado y consulta ese mismo ID.

- FREE recibe `elegibilidad[]` con los productos activos y sus seis criterios;
  `recomendaciones` queda ausente.
- PRO y ENTERPRISE reciben además el ranking de los productos elegibles,
  con monto, plazo y tres razones (banda, destino y capacidad de pago).
- `elegibilidad:[]` indica catálogo activo vacío. Una lista con todos los
  `elegible:false` indica que ningún producto aplica. Si el ranking se calculó
  y ninguno aplica, `recomendaciones` es `[]`.
- Con fuentes fallidas, la capacidad no se considera verificada, los productos
  no califican y el ranking queda ausente, igual que en C-06.

Un producto de otra moneda se incluye en la elegibilidad con criterios de
monto y capacidad incumplidos. No se mezclan importes ni se convierten divisas.
PYME exige empresa; microcrédito exige persona; VERDE admite ambos tipos y
requiere destino de eficiencia energética. Un producto inactivo no aparece
en nuevas evaluaciones.

La elegibilidad corresponde al monto propuesto ajustado, no necesariamente
al solicitado: RF-NEC-01 limita el monto por capacidad y luego se aplica la
tasa y el máximo de cada producto. Los detalles indican el monto y plazo
evaluados. El plazo fuera del rango no califica; no se cambia silenciosamente
el plazo solicitado para forzar una recomendación.

## Capacidad y redondeo

`CreditNeedAnalyzer` conserva CA-01 a CA-04: ingreso observado o el 70 % del
declarado, y cuota máxima del 40 % del ingreso neto menos cuotas vigentes.
`LoanPayments` calcula una anualidad fija a la tasa anual porcentual del
producto, usando `BigDecimal` con precisión DECIMAL128:

- tasa mensual `i = tasaAnual / 1200`;
- valor presente `C × (1 − (1 + i)^−n) / i`;
- monto propuesto = mínimo entre sugerido por RF-NEC-01, máximo del producto
  y valor presente de la cuota máxima; redondeado hacia abajo a dos decimales;
- cuota = monto dividido por el factor de anualidad, redondeada hacia arriba
  a dos decimales. Debe ser menor o igual a la capacidad máxima.

Con tasa cero se usa `monto / n`. Se exige monto positivo y dentro del rango.
Si la capacidad solo permite un monto inferior al mínimo, el producto no
califica. Es una recomendación orientativa sin comisiones, no una aprobación.

## Persistencia, aislamiento e histórico

La migración `V2026_10_18_1100__assessment_eligibility_and_ranking.sql` agrega:

- `assessment.eligibility_snapshots`: una fila por evaluación, incluso con
  catálogo vacío; el JSON guarda productos y criterios ordenados. RLS está
  habilitado y forzado con `tenant_isolation`; la app solo tiene `SELECT, INSERT`.
- `suggested_term_months` y `reasons` en `assessment_recommendations`, con
  validación de plazo y al menos dos razones para los detalles nuevos. Las
  filas históricas de C-06 conservan ambas columnas en null.

Evaluación, elegibilidad, ranking, auditoría y evento se guardan en la misma
transacción. Repetir la misma Idempotency-Key devuelve el snapshot y no vuelve
a consumir cuota ni a consultar el catálogo. Todas las consultas filtran por
el tenant del JWT y además se someten a RLS. No se cambia RLS ni se agregan
privilegios elevados a la aplicación.

Cambiar las bandas, rangos, tasas o actividad del catálogo no modifica un
snapshot ya guardado. Un producto desactivado después puede seguir en el
resultado histórico, porque estaba activo cuando se evaluó. Una evaluación
anterior a E-16 no inventa elegibilidad, plazo ni razones adicionales.

Los mixins de Jackson omiten únicamente esos campos cuando son null, sin
editar modelos generados; conservan las listas vacías cuando se evaluaron.
Las pruebas exigen ausencia de la propiedad JSON, además de ausencia de valor.

La lectura y la repetición idempotente consultan el plan actual del servidor.
Bajar a FREE oculta el ranking sin borrar sus datos; volver a PRO recupera el
mismo ranking. Subir de FREE a PRO no recalcula evaluaciones que no guardaron
ranking: para obtener uno se debe realizar una evaluación nueva.

## Consulta y diagnóstico

Con `API`, `TOKEN_ANALISTA` e `ID_EVALUACION` definidos para la instalación:

```bash
curl --fail-with-body --silent --show-error \
  "$API/credit-assessments/$ID_EVALUACION" \
  -H "Authorization: Bearer $TOKEN_ANALISTA"
```

Un analista puede leer el resultado sin poder administrar `/productos`:
esa ruta exige `TENANT_ADMIN`. Sin token válido se responde 401. Consultar
una evaluación de otro tenant responde 404 y sus snapshots tampoco son
visibles mediante el rol SQL de aplicación.

Ante ausencia de ranking, revisar el plan vigente, el estado PARCIAL y si
la evaluación es histórica. Ante productos no elegibles, revisar los seis
criterios; no actualizar manualmente los snapshots. La interfaz ampliada
con criterios y plazos corresponde a C-16, que consume este contrato.

## Verificación

Con Docker disponible, desde `backend/`:

```bash
./mvnw -B -Dtest=EligibilityEngineTest,LoanPaymentsTest,ProductRecommendationServiceTests,AssessmentRecommendationsTests,RowLevelSecurityCoverageTests test
./mvnw -B clean verify
```

Las pruebas de integración usan PostgreSQL 17 real y `doggycredit_app`.
Cubren los tres tipos, criterios y razones, cuota a la tasa del catálogo,
redondeo, Free/Pro, cambios de plan, ausencia de productos aplicables,
inactivos, moneda distinta, resultados parciales, roles, aislamiento,
inmutabilidad e idempotencia. Las pruebas de cuota incluyen los extremos
de tasas, plazos y capacidad de un centavo.
