# Suspender, reactivar y consultar consumo de tenants (E-14)

Implementa LOS-129 / RF-TEN-04. Requiere un token con rol `PLATFORM_ADMIN`.
El `tenant_id` del token identifica al operador; el UUID de la ruta identifica
la institución administrada. Un `ANALYST`, `TENANT_ADMIN` o `API_CLIENT`
recibe 403 `INSUFFICIENT_ROLE` al listar o modificar tenants activos.

## Contrato y dependencia A-15

Esta entrega incluye solo la parte de LOS-128 necesaria para E-14:
`TenantActual.evaluacionesMes` y `limiteMensual` opcionales, el error
`TENANT_SUSPENDED` en el catálogo y los clientes regenerados desde OpenAPI.
Los otros contratos de A-15 y la vista de confirmación de B-13 siguen en sus
respectivas historias. `/tenant/actual` y la respuesta del PATCH mantienen
su contenido habitual; solo el listado del operador rellena los contadores.

## Consultar plan, estado y consumo

Definir en la terminal `TOKEN_OPERADOR`, `TENANT_ID` y `API` (por ejemplo,
`https://localhost/api/v1`), usando las credenciales de la instalación. El
certificado local debe estar confiado, como indica `franco-sprint1.md`.
No guardar tokens en el repositorio ni en evidencias públicas.

```bash
curl --fail-with-body --silent --show-error "$API/admin/tenants" \
  -H "Authorization: Bearer $TOKEN_OPERADOR" \
  -H 'X-Correlation-Id: operator-list-tenants'
```

Cada fila incluye `plan`, `activo`, `evaluacionesMes` y `limiteMensual`.
El consumo corresponde al mes calendario actual en UTC, exactamente el
contador `billing.monthly_usage`; es cero si no hay fila. Un límite de cero
significa sin límite. Los tenants suspendidos siguen en el listado y su
consumo se conserva. La lectura usa el mecanismo de [ADR-019](../adr/ADR-019-consumo-operador-con-rls.md),
sin desactivar RLS ni dar `BYPASSRLS` a ningún rol nuevo.

## Suspender

```bash
curl --fail-with-body --silent --show-error -X PATCH "$API/admin/tenants/$TENANT_ID" \
  -H "Authorization: Bearer $TOKEN_OPERADOR" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: operator-suspend-001' \
  --data '{"activo":false}'
```

Esperar 200 y `activo:false`. Desde la siguiente petición, cualquier token
del tenant, tanto de persona como de clave de API, recibe 403 con cuerpo
`application/problem+json`, `code:TENANT_SUSPENDED`, `correlationId` y
`timestamp`. Se comprueba el estado en el servidor en cada petición, antes
del controlador, la validación del cuerpo y el rate limit. El token no se
revoca en Keycloak; su renovación no evita la suspensión. No se eliminan
datos ni se consumen evaluaciones al rechazar la petición.

La excepción es `PLATFORM_ADMIN`: sigue operando incluso si su propio tenant
está suspendido, por lo que puede recuperarlo. Las peticiones sin token
conservan el 401 habitual y `/actuator/health` sigue siendo público.
Una petición que ya estaba en ejecución al confirmar el cambio puede terminar;
el control se aplica al ingresar cada nueva petición.

## Reactivar

```bash
curl --fail-with-body --silent --show-error -X PATCH "$API/admin/tenants/$TENANT_ID" \
  -H "Authorization: Bearer $TOKEN_OPERADOR" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: operator-reactivate-001' \
  --data '{"activo":true}'
```

Esperar 200 y `activo:true`. Repetir con el mismo token vigente del tenant una
petición que antes respondía 403, por ejemplo `GET /tenant/actual`: debe
responder 200 inmediatamente, dentro del máximo de un minuto, sin iniciar
sesión de nuevo ni reiniciar servicios. Un token que haya expirado necesita
la renovación OAuth2 habitual.

Repetir el mismo estado es idempotente: responde 200 sin duplicar eventos de
auditoría. Si también se envía `plan`, se conserva el flujo de cambio de plan
existente; para una operación de suspensión enviar solo `activo`.

## Auditoría y diagnóstico

Cada transición real inserta `TENANT_SUSPENDED` o `TENANT_REACTIVATED` en
`audit.access_log` del tenant afectado, con actor del operador, recurso
`TENANT`, UUID administrado y el `X-Correlation-Id` de la petición. El cambio
de estado y su registro comparten transacción: si no se puede auditar,
la suspensión/reactivación no se confirma. La cadena de hash sigue vigente.
El contexto del operador se restaura al terminar, también ante fallos.

Para investigar, consultar la bitácora del tenant afectado por correlación con
las herramientas operativas autorizadas; las filas no se registran bajo el
tenant del operador. No otorgar permisos adicionales a `doggycredit_app` ni a
`doggycredit_usage_reader`, ni deshabilitar políticas de RLS para diagnosticar.

Si un usuario sigue bloqueado, comprobar el UUID del claim `tenant_id`, el
estado del listado y el código concreto del error: `TENANT_SUSPENDED` indica
estado suspendido, `INSUFFICIENT_ROLE` permisos del rol y `TENANT_NOT_FOUND`
un tenant no registrado. La cabecera `X-Tenant-Id` no cambia el tenant del JWT.

## Verificación reproducible

Con Docker disponible:

```bash
cd backend
./mvnw -B -Dtest=TenantSuspensionTests,AdminControllerTests,IdentitySecurityTests,RowLevelSecurityCoverageTests test
./mvnw -B verify
cd ..
python3 ci/check_skipped_tests.py
```

`TenantSuspensionTests` usa PostgreSQL 17 de Testcontainers y el rol real
`doggycredit_app`. Solo sustituye la verificación criptográfica del JWT;
ejecuta la cadena Bearer y la conversión de roles. Prueba varias rutas,
personas y servicio `API_CLIENT`, reactivación con el mismo token, excepción
del operador, roles, UTC y cero consumo, RLS, privilegios mínimos, auditoría
con correlación y rollback ante un fallo al auditar.
