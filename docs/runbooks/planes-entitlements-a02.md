# Planes, entitlements y aislamiento (A-01, A-02, A-04, A-05, A-09)

## Fuente única: `billing.Entitlements`

`Entitlements.of(tenantId)` devuelve el plan vigente con sus límites (0 = sin
límite) y sus funciones (`billing.Feature`). Lee el plan de `tenancy.tenants`
por el puerto `billing.TenantPlans` (lo implementa tenancy, así billing no
depende de tenancy) y los límites de `billing.plans`, en cada llamada: un cambio
de plan se nota sin volver a iniciar sesión.

| Plan | Consultas/mes | Usuarios | Integraciones | Funciones |
| --- | ---: | ---: | ---: | --- |
| FREE | 500 | 3 | 1 | ninguna |
| PRO | sin límite | 50 | sin límite | todas |

`billing.plans` guarda 999 integraciones para PRO ("todas las del catálogo") y
`IntegrationService` usa ese número para contar; `Entitlements` lo publica como
0 (sin límite). Los valores viven en las migraciones de `billing.plans`, no en
`seed/tenants.yaml`, que solo asigna el plan de cada tenant de demo.

`EntitlementsTests` comprueba los valores, que un cambio de plan se nota en la
siguiente consulta y que coinciden con lo que publica la landing en `#planes`
(`web/src/app/landing/landing.ts`): si alguien cambia uno sin el otro, falla.

`GET /tenant/actual` (A-01) devuelve además `evaluacionesMes`, `limiteMensual`,
`limiteUsuarios`, `limiteIntegraciones` y `funcionalidades`; Configuración ›
Plan (A-09) los muestra con la tabla de funciones incluidas o que requieren Pro.
La solicitud simulada «Mejorar a Pro» se retiró en E-09 (LOS-107) para no
prometer una contratación que no existe: el cambio lo hace el operador (A-08).

## Aislamiento en todos los endpoints (A-04)

`OpenApiTenantIsolationTests` lee `contracts/openapi/doggycredit-api.v1.yaml`:

1. Cada operación del contrato debe tener su prueba de aislamiento en `COVERAGE`.
   Agregar un endpoint sin registrarla hace fallar la suite.
2. El tenant B recorre todas las operaciones GET y ninguna respuesta contiene
   datos sembrados en el tenant A; la lectura por id responde 404.
3. El tenant B intenta modificar productos, políticas, consentimientos y claves
   de API del tenant A por su id: 404 y el recurso queda igual.

`RowLevelSecurityCoverageTests` sigue exigiendo RLS forzado en toda tabla con
`tenant_id`.

## Rol propio de la aplicación (A-05, DEBT-018)

La app entra como `doggycredit_app` (LOGIN, sin superusuario ni `BYPASSRLS`);
Flyway usa el administrador. `ProductionDataSourceWiringTest` comprueba que
`RESET ROLE` la deja en `doggycredit_app`, que RLS sigue aplicando y que no puede
`SET ROLE` al administrador.
