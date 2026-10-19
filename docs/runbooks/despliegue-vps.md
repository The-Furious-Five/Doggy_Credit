# Despliegue en VPS (CI/CD)

Workflow: `.github/workflows/ci-cd.yml`.

- **PR a main**: backend, web, contracts (incluye oasdiff), identity, ML y E2E. No despliega.
- **Push a main** (o *Run workflow* manual sobre main): los mismos jobs y, solo si
  todos pasan, `deploy`. El PR queda para revisión manual antes de fusionar.
- **Reintento mensual**: el día 1 a las 06:17 UTC (02:17 en Bolivia), el mismo
  workflow vuelve a comprobar `main` y despliega sólo si sus seis revisiones pasan.

`deploy` compila **en GitHub Actions** las imágenes `doggycredit-api` y
`doggycredit-web`, las publica en GHCR con el SHA del commit y envía al VPS un
paquete de configuración (compose, Caddyfile y realm con el dominio público; sin
código fuente). En el VPS `scripts/deploy.sh` descarga las imágenes, arranca con
`infra/compose.prod.yaml`, valida `/actuator/health` por HTTPS y, si algo falla,
vuelve a la release anterior. Al final, `scripts/smoke_release.py` comprueba
desde Internet que `/release.json` es el SHA del commit, que la SPA, Keycloak y la
API responden como se espera y que actuator está cerrado. Ver
[release-sprint.md](release-sprint.md).

## Cuota gratuita de Actions

Con el presupuesto de Actions en **0 USD** y el bloqueo de uso adicional activo,
GitHub detiene los jobs cuando se agotan los minutos incluidos. Los cambios
fusionados permanecen en `main`; el VPS sigue sirviendo su release anterior.
Un workflow fallido no se reanuda por sí solo al renovarse la cuota.

El evento `schedule` del día 1 inicia una nueva ejecución sobre la versión
actual de `main` tras la renovación mensual. Ejecuta las seis revisiones y el
mismo despliegue con rollback y smoke test, sin cambiar el presupuesto ni
requerir entrar manualmente al VPS. GitHub puede retrasar el horario programado;
si persiste un bloqueo de facturación o falla una revisión, no se despliega.

Para comprobar el resultado, revisar el workflow CI/CD y comparar el SHA de
`https://doggycredit.los5furiosos.cloud/release.json` con el de esa ejecución.
Mientras no coincidan y pase el smoke test, el despliegue sigue pendiente.

## Despliegue personal con Mac temporal

`deploy-once.yml` acepta sólo ejecuciones manuales. Un único job repite las seis
revisiones y luego construye imágenes Linux x64, despliega con rollback y
comprueba producción. No modifica los runners del workflow CI/CD habitual.

Antes de ejecutarlo se autorizan temporalmente `DC_ONCE_DEPLOY_ACTOR` (usuario
de GitHub) y `DC_ONCE_DEPLOY_SHA` (commit completo de `main`). El workflow exige
que actor, SHA e input coincidan. Se registra un runner de ese repositorio con
una etiqueta aleatoria `doggycredit-once-...`, `--no-default-labels` y
`--ephemeral`. Así los jobs habituales del equipo no lo seleccionan y GitHub
lo retira automáticamente después de ese único job.

El runner se ejecuta en primer plano desde una carpeta temporal, sin instalar
un servicio ni un inicio automático en el Mac. Usa un proyecto Compose propio
con puertos de prueba en loopback. Sus claves SSH y configuración de Docker
están en carpetas temporales, sin reemplazar las credenciales del usuario.
Al finalizar se comprueba la baja del runner, se borran ambas autorizaciones y
se retira la carpeta temporal. El VPS sigue funcionando al apagar el Mac.

## Banco y billetera en cada release (E-04)

El paquete `releases/<sha>` contiene los mappings y respuestas de banco y
billetera de ese mismo commit, junto con `check_simulators.sh`. WireMock mantiene
la imagen fijada `wiremock/wiremock:3.9.1`; las imágenes de API y web usan la
etiqueta `<sha>` de GHCR. Los simuladores se actualizan al montar los datos de la
release elegida, sin construir imágenes en el VPS.

En producción, ambos montajes son de sólo lectura y no crean automáticamente
directorios que falten. Los puertos 8090 y 8091 permanecen cerrados al host; la
API los consulta por `wiremock-bank:8080` y `wiremock-wallet:8080` y espera a que
estén saludables antes de arrancar.

`deploy.sh` valida Compose, descarga las imágenes y espera la salud del stack.
Después comprueba `/__admin/health` y una consulta del documento ficticio
`CI-DEMO-0001001` en cada proveedor. Sólo cambia `current` cuando los simuladores
y el endpoint HTTPS de la API responden. Un fallo restaura la release anterior
con sus propios mappings y espera su salud. `DC_DEPLOY_BASE` permite elegir otra
carpeta base; por defecto sigue siendo `/srv/doggycredit`.

El CI ejecuta la misma comprobación contra Compose y valida el override de
producción. Para repetir la comprobación dentro de una release en el VPS:

```bash
cd /srv/doggycredit/current
bash scripts/check_simulators.sh --env-file /srv/doggycredit/.env \
  --env-file infra/release.env -f infra/compose.yaml -f infra/compose.prod.yaml
```

Para comprobar una entrega por etiqueta, seleccionar `releases/<sha>`, ejecutar
`bash scripts/deploy.sh` y verificar que `current` apunta a ese SHA y que la
comprobación anterior informa ambos simuladores saludables. Esa prueba requiere
el VPS y sus credenciales; los tests locales con comandos simulados no la
sustituyen.

## Acceso de la app iOS (automático)

El generador produce `doggycredit-realm.json` y `doggycredit-mobile.json` desde
la misma definición del cliente público `doggycredit-mobile`. El cliente usa
Authorization Code, PKCE S256 obligatorio y la redirección exacta
`doggycredit://oauth/callback`; incluye los scopes `basic`, `profile`, `email`,
`roles` y los mappers `tenant_id` y `api-audience`.

En cada despliegue, `sync_mobile_client.sh` se ejecuta dentro de Keycloak con
las credenciales administradoras que ya usa el pipeline. Crea el cliente si no
existe o actualiza su configuración conservando su ID si ya existe. Funciona
con el realm persistido: no depende de `--import-realm`, no recrea el realm y
no requiere Python ni jq en el VPS. Si falla, el despliegue restaura la release
anterior antes de activar `current`.

Antes de validar Compose, `ensure_deploy_env.sh` genera una contraseña aleatoria
para `DC_SEED_PLATFORM_ADMIN_PASSWORD` si falta o está vacía en el `.env` de
producción. La guarda una sola vez con permiso 600, sin imprimirla y sin cambiar
un valor existente. Esto permite desplegar desde releases anteriores que aún
no tenían esa variable. No actualiza contraseñas de usuarios ya importados.

CI prueba crear el cliente en un realm existente, corregir una configuración
incompleta y repetir la sincronización sin duplicados. También ejecuta el login
móvil real, valida sus claims, consulta las rutas que usa iOS, renueva el token
y cierra la sesión. Con el mismo token móvil crea una evaluación sintética,
lista su caso de revisión, aprueba/rechaza con justificación y comprueba que
una segunda resolución responde 409. La migración de revisión se ejecuta después
de las migraciones ya publicadas, por lo que también actualiza bases existentes.
El smoke test de producción verifica desde Internet que el
cliente móvil presenta el formulario de acceso con el callback y PKCE esperados.

Una vez fusionado el cambio a `main`, CI/CD aplica todo sin intervención manual
en el VPS. La app iOS usa `https://doggycredit.los5furiosos.cloud` por defecto;
para consumir datos reales se inicia sesión con una cuenta de la institución.

## Preparar el VPS (una vez)

1. Docker Engine + Docker Compose **>= 2.24**, `curl`. Puertos 443 abiertos y DNS
   del dominio apuntando al VPS (Caddy obtiene el certificado por TLS-ALPN).
2. Usuario de despliegue en el grupo `docker`, dueño de `/srv/doggycredit`.
3. `/srv/doggycredit/.env` (permiso 600) con las variables de `.env.example` y
   valores reales. Obligatorias en producción además: `DC_MASTER_KEY` propia
   (la API arranca con perfil `prod` y rechaza la clave de demo) y
   `DC_HTTP_PORT=443`. `DC_KEYCLOAK_ISSUER_URI` la fija el pipeline.

## Configurar GitHub (una vez)

Settings → Environments → `production` (opcional: *required reviewers* para
aprobar cada despliegue).

| Tipo | Nombre | Valor |
|------|--------|-------|
| Variable | `DC_KEYCLOAK_ISSUER_URI` | `https://DOMINIO/auth/realms/doggycredit` |
| Secret | `VPS_HOST` | IP o DNS del VPS |
| Secret | `VPS_USER` | usuario de despliegue |
| Secret | `VPS_SSH_KEY` | clave privada ed25519 (su pública en `authorized_keys`) |
| Secret | `VPS_KNOWN_HOSTS` | salida de `ssh-keyscan VPS_HOST`, huella verificada |

GHCR usa el `GITHUB_TOKEN` del job: no hace falta un PAT. El VPS inicia sesión
solo durante el despliegue y la cierra al terminar.

## Operación

- Release activa: `/srv/doggycredit/current` → `releases/<sha>`. Se conservan 3
  releases y las imágenes de la actual y la anterior.
- Volver a una versión: *Run workflow* no despliega un SHA arbitrario; hacer
  `git revert` en main. Manual en el VPS:
  `cd /srv/doggycredit/releases/<sha> && bash scripts/deploy.sh`.
- Logs: `cd /srv/doggycredit/current && docker compose --env-file ../../.env --env-file infra/release.env -f infra/compose.yaml -f infra/compose.prod.yaml logs -f doggycredit-api`.
- Las migraciones Flyway no se revierten: deben ser compatibles con la release anterior.
- WireMock no publica puertos en el VPS. Grafana solo escucha en loopback:
  `ssh -L 3300:127.0.0.1:3300 usuario@VPS` y abrir http://localhost:3300.

## Pendiente para producción plena

Keycloak sigue en `start-dev` (base H2 en volumen). Pasarlo a `start` con
PostgreSQL y `--hostname` es un cambio aparte que conviene probar primero en local.
