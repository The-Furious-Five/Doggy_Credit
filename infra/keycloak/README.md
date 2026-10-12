# Realm DoggyCredit

Generado exclusivamente desde `seed/tenants.yaml` mediante
`python scripts/generate_realm.py` (dependencias en `scripts/requirements.txt`).
El JSON versionado contiene referencias a variables, nunca secretos reales.
Compose pasa las variables DC_SEED_* ya declaradas y ejecuta `--import-realm`.

Cliente SPA público `doggycredit-web`, Authorization Code + PKCE S256 obligatorio,
sin password grant. Clientes M2M separados por institución y cuentas de servicio
con `tenant_id`. Usuarios y servicios comparten el mapper de tenant y la audiencia
`doggycredit-api`. Los roles provienen de la semilla.

El volumen persiste el realm. La importación solo crea realms ausentes; no borra
ni actualiza los existentes. Ver [runbook de Franco](../../docs/runbooks/franco-sprint1.md).

## MFA obligatorio para TENANT_ADMIN (A-14, RF-SEG-06)

El generador marca a cada usuario `TENANT_ADMIN` de la semilla con la acción
requerida `CONFIGURE_TOTP`: en su primer inicio de sesión, tras la contraseña,
Keycloak le hace registrar una app de autenticación (TOTP), y desde ahí le pide
el código en cada ingreso. No se siembra ningún segundo factor: cada
administrador registra el suyo. `ANALYST`, el operador y las cuentas de
servicio no cambian.

Como el resto del realm, solo aplica al importarlo. Un realm ya importado (el
de la VM, o un volumen local anterior) no se actualiza solo; para exigirlo sin
borrar datos, por cada administrador:

```bash
docker compose -p doggycredit -f infra/compose.yaml --env-file .env exec keycloak bash -c \
  '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master \
     --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" &&
   id=$(/opt/keycloak/bin/kcadm.sh get users -r doggycredit -q username=admin.progreso -q exact=true --fields id --format csv --noquotes) &&
   /opt/keycloak/bin/kcadm.sh update "users/$id" -r doggycredit -s "requiredActions=[\"CONFIGURE_TOTP\"]"'
```

## Tema de acceso

`themes/doggycredit` hereda `keycloak.v2` y solo cambia color, tipografía y forma
con los valores del Design System (fondo pino, tarjeta neutra, acción verde,
claro/oscuro automático). Compose lo monta en `/opt/keycloak/themes`; `start-dev`
no cachea temas, así que basta recargar la página al editar el CSS.

El realm generado ya declara `loginTheme: doggycredit`. Un realm importado antes
de este cambio no se actualiza solo; aplicarlo una vez sin borrar datos:

```bash
docker compose -p doggycredit -f infra/compose.yaml --env-file .env exec keycloak bash -c \
  '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master \
     --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" &&
   /opt/keycloak/bin/kcadm.sh update realms/doggycredit -s loginTheme=doggycredit \
     -s "displayNameHtml=Doggy<span class=\"dc-wordmark-accent\">Credit</span>"'
```
