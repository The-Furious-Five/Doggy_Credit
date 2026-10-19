# Equipo, roles y enmascarado (A-10 / LOS-71)

## Invitar a una persona

`POST /tenant/actual/equipo` (solo `TENANT_ADMIN`; en la consola, Configuración ›
Equipo › **Invitar**). El backend:

1. Toma el `tenant_id` del JWT, nunca del cuerpo.
2. Cuenta las cuentas humanas **habilitadas** del tenant en Keycloak, invitaciones
   pendientes incluidas, y las compara con `billing.plans.max_usuarios` del plan
   vigente (FREE = 3; 0 = sin límite). Si no hay lugar responde
   `409 PLAN_LIMIT_REACHED` y no crea nada. Desactivar una cuenta libera su lugar.
3. Crea el usuario en Keycloak (usuario = email) con el atributo `tenant_id`, el rol
   y la acción requerida `UPDATE_PASSWORD` (un `TENANT_ADMIN` suma `CONFIGURE_TOTP`,
   como los sembrados de A-14). Si ya existe responde `409 MEMBER_ALREADY_EXISTS`.
   Si falla la asignación del rol, borra el usuario: no queda una cuenta a medias.
4. Pide a Keycloak el correo con el enlace (`execute-actions-email`). Es de mejor
   esfuerzo: sin SMTP en el realm solo queda un aviso en el log.
5. Audita `TEAM_MEMBER_INVITED` en `audit.access_log`.

La persona aparece en la tabla como **Invitación enviada** hasta que define su
contraseña.

### Sin correo configurado (local y VM del MVP)

El realm sembrado no tiene SMTP. Para que la persona invitada entre, fija una
contraseña temporal; Keycloak le pedirá cambiarla en el primer ingreso:

```bash
docker compose -p doggycredit -f infra/compose.yaml --env-file .env exec keycloak bash -c \
  '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master \
     --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" &&
   /opt/keycloak/bin/kcadm.sh set-password -r doggycredit --username rosa@institucion.bo --new-password "<temporal>" --temporary'
```

## Enmascarado de documentos y montos

La preferencia **Ocultar documentos y montos a roles sin permiso**
(`tenancy.tenant_preferences.enmascarar_datos`, activa por defecto) la aplica el
**servidor** (`shared.SensitiveDataPolicy`, implementada en tenancy) en cada
petición:

| Quién | Ve |
| --- | --- |
| `ANALYST` con la preferencia activa | documento con solo los últimos 4 caracteres (`••••••5-LP`) y montos como `••••••` |
| `TENANT_ADMIN`, `PLATFORM_ADMIN` | el dato completo |
| Integraciones M2M (`API_CLIENT`) | el dato completo: consultan sus propias solicitudes |

Respuestas enmascaradas: `GET /solicitantes` y `/solicitantes/{id}` (documento,
ingresos y montos del Perfil 360), `GET /revision-manual` (documento y monto
solicitado), `GET /credit-assessments` (monto solicitado) y los montos de
`GET /analytics/resumen`. El alta (`POST /solicitantes`) devuelve el dato completo
a quien lo acaba de escribir. La consola sigue enmascarando por su cuenta y, si
recibe un monto ya enmascarado, lo muestra tal cual.

Pruebas: `SensitiveDataMaskingTests` (roles, preferencia y aislamiento entre
tenants), `ManualReviewTests`, `TenantControllerTests` (invitación y límite) y, en
la web, `team-invite.spec.ts` y `settings.spec.ts`.
