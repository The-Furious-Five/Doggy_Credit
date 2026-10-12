# Despliegue en VPS (CI/CD)

Workflow: `.github/workflows/ci-cd.yml`.

- **PR a main**: backend, web, contracts (incluye oasdiff), identity y ML. No despliega.
- **Push a main** (o *Run workflow* manual sobre main): los mismos jobs y, solo si
  todos pasan, `deploy`. El PR queda para revisión manual antes de fusionar.

`deploy` compila **en GitHub Actions** las imágenes `doggycredit-api` y
`doggycredit-web`, las publica en GHCR con el SHA del commit y envía al VPS un
paquete de configuración (compose, Caddyfile y realm con el dominio público; sin
código fuente). En el VPS `scripts/deploy.sh` descarga las imágenes, arranca con
`infra/compose.prod.yaml`, valida `/actuator/health` por HTTPS y, si algo falla,
vuelve a la release anterior. Al final, `scripts/smoke_release.py` comprueba
desde Internet que `/release.json` es el SHA del commit, que la SPA, Keycloak y la
API responden como se espera y que actuator está cerrado. Ver
[release-sprint.md](release-sprint.md).

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
