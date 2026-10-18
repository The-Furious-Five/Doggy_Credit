# Administración de tenants por API (A-08 / LOS-69)

El MVP no tiene consola de plataforma: el operador administra las instituciones
por API con un token de rol `PLATFORM_ADMIN` (en la semilla, `operador.demo`). Un
`ANALYST`, `TENANT_ADMIN` o `API_CLIENT` recibe 403 `INSUFFICIENT_ROLE`
(`AdminControllerTests`, `PlanChangeTests`). El cambio de plan es simulado: no
hay cobro (DEBT-010).

Definir `API` (por ejemplo `https://localhost/api/v1`), `TOKEN_OPERADOR` y
`TENANT_ID`, como en [suspension-tenants-e14.md](suspension-tenants-e14.md). No
guardar tokens en el repositorio ni en evidencias públicas.

## Listar instituciones

```bash
curl --fail-with-body --silent --show-error "$API/admin/tenants" \
  -H "Authorization: Bearer $TOKEN_OPERADOR" \
  -H 'X-Correlation-Id: operator-list-001'
```

Cada fila trae `id`, `nombre`, `plan`, `activo`, y el consumo del mes
(`evaluacionesMes`, `limiteMensual`; 0 = sin límite).

## Cambiar el plan

La ruta del contrato es `PATCH /admin/tenants/{id}` (el ticket original decía
`PUT /admin/tenants/{id}/plan`; el contrato de D-10 unificó plan y estado en un
solo PATCH).

```bash
curl --fail-with-body --silent --show-error -X PATCH "$API/admin/tenants/$TENANT_ID" \
  -H "Authorization: Bearer $TOKEN_OPERADOR" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: operator-plan-001' \
  --data '{"plan":"PRO"}'
```

- Subir (`FREE` → `PRO`) es inmediato: las compuertas leen el plan en cada
  petición, sin volver a iniciar sesión (A-11). Cancela una bajada programada y
  recupera las integraciones desactivadas durante una gracia.
- Bajar desde el operador también es inmediato y abre 30 días de gracia; las
  integraciones que exceden el plan nuevo se desactivan sin borrarse (A-12).
- Un plan inexistente responde 400 y no cambia nada. Repetir el mismo plan no
  hace nada.
- Suspender o reactivar va por el mismo PATCH con `{"activo":false|true}`.

## Verificar la auditoría

Cada cambio queda en `audit.access_log` del tenant afectado, con el
`X-Correlation-Id` enviado: `PLAN_UPGRADED`, `PLAN_DOWNGRADED`,
`TENANT_SUSPENDED` o `TENANT_REACTIVATED`. El administrador del tenant lo ve en
Configuración › Seguridad.

Desde la base, con la conexión de administración (RLS no aplica a ese rol):

```bash
docker compose -p doggycredit -f infra/compose.yaml --env-file .env exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v tenant="$1"' sh "$TENANT_ID" <<'SQL'
select created_at, actor, action, correlation_id from audit.access_log
 where tenant_id = :'tenant'::uuid and resource_type = 'TENANT'
 order by created_at desc limit 10;
SQL
```
