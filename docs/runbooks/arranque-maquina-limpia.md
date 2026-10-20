# Arranque en una máquina limpia en ≤ 15 min (B-14 / LOS-141)

Objetivo: de un equipo con solo Docker y Git a una evaluación crediticia completa
en `https://localhost`, sin Java, Node ni Python instalados (la API y la SPA se
compilan dentro de Docker).

> **Tiempo medido: pendiente: tiempo medido por [persona], [fecha].**
> Quien siga este runbook en una máquina limpia (sin imágenes ni volúmenes de
> Docker previos) cronometra desde el paso 1 hasta ver el resultado del paso 7 y
> completa esta línea. Hasta entonces el «≤ 15 min» es una meta, no una medición.
> La primera compilación (imágenes de Maven, Node y Keycloak) domina el tiempo y
> depende de la red.

## Requisitos

| Qué | Versión | Nota |
| --- | --- | --- |
| Docker Engine o Docker Desktop | con Compose v2 (`docker compose`, no `docker-compose`) | Probado con Docker 29.0.1 y Compose v2.40.3 (el archivo valida con `docker compose config`). El override de producción pide Compose ≥ 2.24; en local no se usa. |
| Git | cualquiera reciente | |
| Una app de autenticación TOTP | Google Authenticator, Aegis, 1Password, etc. | Solo para el usuario `TENANT_ADMIN` (paso 6). |
| Puertos libres en el host | 443, 3300, 8089, 8090, 8091, 8092 | Se cambian en `.env` (`DC_HTTP_PORT`, `DC_GRAFANA_HOST_PORT`, `DC_WIREMOCK_*_HOST_PORT`). |

No se necesita Java, Node ni Python para arrancar. Sí para correr las pruebas
(ver [franco-sprint1.md](franco-sprint1.md#pruebas)).

## 1. Clonar

```bash
git clone https://github.com/The-Furious-Five/Doggy_Credit.git
cd Doggy_Credit
```

(Use la URL del remoto que tenga acceso; el nombre de la carpeta no importa.)

## 2. Crear el `.env`

```bash
cp .env.example .env
```

Los valores de ejemplo sirven tal cual para desarrollo local: son contraseñas de
contenedores que solo viven en su Docker. Las claves marcadas «SOLO NICOLE» se
dejan con el valor de ejemplo; no las cambie salvo que el equipo se lo pida.
`.env` está en `.gitignore`: no se versiona.

Contraseñas de demo (de `.env.example`, todas `dev_only_change_me`):

| Usuario | Rol | Tenant | Contraseña en `.env` |
| --- | --- | --- | --- |
| `analista.progreso` | ANALYST | Progreso Andino (Gratis) | `DC_SEED_ANALYST_PASSWORD` |
| `admin.progreso` | TENANT_ADMIN | Progreso Andino (Gratis) | `DC_SEED_ADMIN_PASSWORD` |
| `admin.vecina` | TENANT_ADMIN | Fintech Vecina (Pro) | `DC_SEED_ADMIN_PASSWORD_2` |
| `operador.demo` | PLATFORM_ADMIN | Fintech Vecina (Pro) | `DC_SEED_PLATFORM_ADMIN_PASSWORD` |

La fuente de estos usuarios es `seed/tenants.yaml`; el realm de Keycloak ya viene
generado en `infra/keycloak/doggycredit-realm.json` (no hace falta correr
`scripts/generate_realm.py`).

## 3. Levantar el stack

```bash
docker compose --env-file .env -f infra/compose.yaml up -d --build --wait
docker compose --env-file .env -f infra/compose.yaml ps
```

`--wait` bloquea hasta que todos los servicios con healthcheck estén `healthy`.
La primera vez compila el backend (Maven) y la SPA (Angular) dentro de Docker: es
el paso más largo. Servicios: `postgres`, `keycloak`, `wiremock-bureau`,
`wiremock-bank`, `wiremock-bank-dos`, `wiremock-wallet`, `otel-lgtm` (Grafana),
`doggycredit-api` y `caddy` (entrada única HTTPS).

## 4. Confiar en la CA de Caddy

Caddy firma `localhost` con su CA interna (volumen `caddy_data`). Se confía una
sola vez por máquina; no es un secreto.

```bash
docker compose --env-file .env -f infra/compose.yaml cp \
  caddy:/data/caddy/pki/authorities/local/root.crt ./doggycredit-root.crt
```

Luego, según el sistema:

```bash
# macOS
sudo security add-trusted-cert -d -r trustRoot \
  -k /Library/Keychains/System.keychain ./doggycredit-root.crt
# Linux (Debian/Ubuntu)
sudo cp ./doggycredit-root.crt /usr/local/share/ca-certificates/doggycredit-root.crt
sudo update-ca-certificates
```

```powershell
# Windows (PowerShell como administrador)
certutil -addstore -f ROOT .\doggycredit-root.crt
```

Reinicie el navegador. (Firefox usa su propio almacén: importe el `.crt` en
Ajustes › Privacidad y seguridad › Certificados.) Borre `doggycredit-root.crt` al
terminar; no se versiona. Si se borra el volumen `caddy_data`, Caddy genera otra
CA y hay que repetir este paso.

## 5. Primer ingreso sin MFA: el analista

1. Abra <https://localhost> y pulse **Iniciar sesión**. Lo lleva a Keycloak
   (`/auth/...`): es el comportamiento esperado, la SPA nunca recibe contraseñas.
2. Usuario `analista.progreso`, contraseña el valor de `DC_SEED_ANALYST_PASSWORD`.
3. Vuelve a la consola en `/app/resumen` con el tenant «Progreso Andino».

## 6. Ingreso del TENANT_ADMIN (exige TOTP, A-14)

Los usuarios `TENANT_ADMIN` (`admin.progreso`, `admin.vecina`) tienen la acción
requerida `CONFIGURE_TOTP`: no hay segundo factor sembrado, cada uno registra el
suyo.

1. Cierre sesión y entre con `admin.progreso` y `DC_SEED_ADMIN_PASSWORD`.
2. Tras la contraseña, Keycloak muestra un código QR (o la clave para ingreso
   manual). Escanéelo con su app TOTP, escriba el código de 6 dígitos y un nombre
   de dispositivo, y guarde.
3. En los ingresos siguientes Keycloak pide el código de la app.

Si pierde el dispositivo, recree el volumen de Keycloak (`down -v`, ver abajo) o
quite la credencial desde la consola de Keycloak (`https://localhost/auth/admin`,
usuario `DC_KEYCLOAK_ADMIN_USER` y `DC_KEYCLOAK_ADMIN_PASSWORD`). Las pruebas
automáticas resuelven el TOTP solas; ver [e2e/README.md](../../e2e/README.md).

## 7. Una evaluación de punta a punta

Con el analista (o el admin) en la consola:

1. Menú **Nueva evaluación**. Titular sintético con datos en el buró, el banco y
   la billetera simulados: documento `CI-DEMO-0001001`, actividad «Comercio
   minorista», ingreso mensual declarado `6500`, monto solicitado `15000`,
   destino «Capital de trabajo». **Continuar**.
2. **Consentimiento**: marque al menos «Consultar burós de crédito» (obligatorio;
   sin él la vista avisa «Falta el consentimiento obligatorio»). **Continuar**.
3. **Fuentes**: el buró aparece «Conectado». **Evaluar**.
4. **Resultado**: score con banda (A–E), capacidad de pago, decisión
   (Preaprobado, Revisión manual o Solicitar datos) y «Principales razones».
5. **Ver perfil completo**: Perfil 360, pestañas Resumen, Score y Fuentes.
6. Opcional: **Más acciones › Revocar consentimiento**; el perfil pasa a «Sin
   consentimiento vigente».

El tenant Gratis solo consulta el buró (banco y billetera salen «No consultada»);
para ver las tres fuentes consolidadas use `admin.vecina` (Pro) y conecte «Core
bancario» y «Billetera móvil» en **Integraciones** antes del paso 1. Este mismo
recorrido es la prueba automática `e2e/tests/credit-flow.spec.ts`.

Verificación de apoyo: Grafana en <http://localhost:3300> (trazas y logs por
`X-Correlation-Id`) y el simulador del buró en <http://localhost:8089>.

## Problemas comunes

| Síntoma | Causa y solución |
| --- | --- |
| `falta DC_DB_PASSWORD en .env` (o cualquier `falta DC_...`) | No hay `.env` o está incompleto. Repita `cp .env.example .env` y pase `--env-file .env`. |
| `port is already allocated` / `bind: address already in use` en 443 | Otro servicio usa el 443. Ponga `DC_HTTP_PORT=8443` en `.env` y entre por `https://localhost:8443`; el issuer (`DC_KEYCLOAK_ISSUER_URI`) y las URIs de retorno del realm asumen el 443, así que lo más simple es liberar el 443. Lo mismo para 3300, 8089–8092 con sus variables. |
| El navegador avisa «conexión no privada» | No se confió en la CA (paso 4) o no se reinició el navegador. |
| `--wait` expira o un servicio queda `unhealthy` | `docker compose --env-file .env -f infra/compose.yaml logs <servicio>`. Keycloak tarda en el primer arranque (healthcheck con `start_period` de 30 s y hasta 30 reintentos); espere y repita `ps`. |
| Pantalla en blanco o `502` tras levantar | `caddy` espera a que la API esté `healthy`; confirme con `ps` que `doggycredit-api` lo está. |
| Keycloak pide un código que no tengo | Su usuario es `TENANT_ADMIN` y ya registró TOTP. Use la app, o recree el volumen (siguiente fila). |
| Cambié `seed/tenants.yaml` o las contraseñas y no se reflejan | Keycloak solo importa el realm la primera vez. Para empezar de cero (borra datos locales y los TOTP): `docker compose --env-file .env -f infra/compose.yaml down -v` y repita desde el paso 3. Luego repita el paso 4 (la CA nueva). |
| `Invalid parameter: redirect_uri` al iniciar sesión | Se entró por una URL distinta de `https://localhost`. Use exactamente esa. |
| `429` o `PLAN_LIMIT_REACHED` al evaluar | El plan Gratis tiene cuota mensual y un límite por minuto, y una sola integración. Use el tenant Pro (`admin.vecina`) o espere. |
| El build del backend o de la SPA falla por red | Es la descarga de dependencias dentro de Docker; repita el comando, el caché de capas conserva lo ya hecho. |

Apagar sin borrar datos: `docker compose --env-file .env -f infra/compose.yaml down`.
