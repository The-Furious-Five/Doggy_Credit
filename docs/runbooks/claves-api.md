# Claves de API (D-09)

Cada clave de API de un tenant es un cliente OAuth 2.0 `client_credentials`
de Keycloak. La API lo crea y lo borra con la cuenta de servicio
`doggycredit-api-admin` (roles de `realm-management`: `manage-clients`,
`view-clients`, `manage-users`, `view-users`, `view-realm`).

- El token de una clave lleva `tenant_id` fijo (mapper de claim constante), el
  rol `API_CLIENT`, la audiencia `doggycredit-api` y `dc_entorno`
  (`sandbox` | `produccion`).
- El secreto se muestra una sola vez. En la base (`tenancy.api_keys`) solo
  quedan sus últimos 4 caracteres.
- Revocar borra el cliente de Keycloak: deja de obtener tokens nuevos, y los
  ya emitidos vencen solos (5 min, igual que DEBT-012).
- `produccion` exige plan PRO o ENTERPRISE (`FEATURE_NOT_IN_PLAN` en FREE).
- El token de `doggycredit-api-admin` no sirve contra la API: no tiene
  audiencia ni `tenant_id` (lo prueba `KeycloakApiKeyIntegrationTests`).

## Variable nueva

`DC_KEYCLOAK_API_ADMIN_SECRET` (— SOLO NICOLE —). Keycloak la usa al importar
el realm y la API, para autenticarse. Tiene que ser la misma en los dos.
Compose no arranca sin ella.

## Equipo y atributo del tenant

Keycloak 26 filtra los atributos de usuario que no están definidos en el perfil.
`scripts/generate_realm.py` declara `tenant_id` como atributo gestionado de un
solo valor UUID, visible y editable sólo por administradores de Keycloak. Los
usuarios conservan sus permisos habituales sobre nombre y correo. La cuenta
administradora de la API sigue sin tenant ni audiencia de la API.

Esto permite que la Admin REST API devuelva `attributes.tenant_id` y que
`listTeamMembers` filtre el equipo por tenant y descarte cuentas de servicio.
Sin esa configuración la consulta devuelve un equipo vacío, aunque los
usuarios estén sembrados en el realm.

En un realm **ya existente**, `--import-realm` no actualiza el perfil. En la
consola administradora, abrir el realm `doggycredit` → Realm settings → User
profile y agregar `tenant_id`: un solo valor, permisos View/Edit sólo Admin,
opcional y validador Pattern para UUID. El JSON generado contiene la definición
en `components.org.keycloak.userprofile.UserProfileProvider[0].config` bajo
`kc.user.profile.config`. Aplicar ese atributo al perfil actual; conservar los
demás atributos y mantener deshabilitados los atributos no gestionados.

Referencia: [perfiles y atributos de Keycloak](https://www.keycloak.org/docs/latest/server_admin/index.html#_user-profile).

## Realm ya importado (Docker local con volumen viejo, VPS)

La importación solo crea realms que no existen, así que en un realm ya
importado el cliente no aparece solo. Hay que crearlo una vez, con el mismo
secreto que `DC_KEYCLOAK_API_ADMIN_SECRET`:

```bash
docker compose -p doggycredit -f infra/compose.yaml --env-file .env exec \
  -e SECRET="$DC_KEYCLOAK_API_ADMIN_SECRET" keycloak bash -c '
  kc=/opt/keycloak/bin/kcadm.sh
  $kc config credentials --server http://localhost:8080/auth --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" &&
  $kc create clients -r doggycredit -s clientId=doggycredit-api-admin -s publicClient=false \
    -s standardFlowEnabled=false -s directAccessGrantsEnabled=false \
    -s serviceAccountsEnabled=true -s "secret=$SECRET" &&
  $kc add-roles -r doggycredit --uusername service-account-doggycredit-api-admin \
    --cclientid realm-management --rolename manage-clients --rolename view-clients \
    --rolename manage-users --rolename view-users --rolename view-realm'
```

En el VPS: primero agregar `DC_KEYCLOAK_API_ADMIN_SECRET` a
`/srv/doggycredit/.env`, después correr lo anterior (con
`-f infra/compose.prod.yaml` y el `.env` del VPS) y recién entonces desplegar.
Si falta la variable, el deploy falla y `deploy.sh` vuelve a la release
anterior.

## Cómo se prueba

- `KeycloakApiKeyIntegrationTests`: Keycloak real (Testcontainers, mismo realm
  generado). La clave obtiene un token del tenant correcto y, revocada, ya no
  obtiene tokens. También verifica que la Admin REST API conserve el tenant
  de los usuarios humanos y que los equipos y roles estén aislados por tenant.
- `scripts/test_realm.py`: perfil administrado, permisos del atributo y
  correspondencia entre el generador y el realm versionado.
- `ApiKeyControllerTests`: rol, plan, aislamiento por tenant, revocación y
  falla de Keycloak, con Keycloak simulado.
- `e2e/identity_smoke.py`: con Compose completo, un TENANT_ADMIN crea una
  clave, la usa contra la API y la revoca.
