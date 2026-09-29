# Entrega Franco — Sprint 1

Alcance: §9.5 de DoggyCredit_Guia_Induccion_Sprint1.pdf: SPIKE-2, SPIKE-5,
SPIKE-6 y HAB-05. Analytics y k6 son responsabilidades futuras, sin tarea
implementable asignada en este sprint. No se implementan las historias de
los otros integrantes. El Excel y el documento normativo de arquitectura
referenciados por el PDF no se encuentran en este checkout.

## Preparar y arrancar

Requiere Docker Compose, Java 21, Node 22.22.3 o superior compatible con
Angular 22, y Python 3.12+. Usar el `.env` entregado por Nicole. No generar
nuevos secretos. El issuer local es `http://localhost:8080/auth/realms/doggycredit`.

```bash
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements.txt
.venv/bin/python scripts/generate_realm.py
bash scripts/generate_clients.sh
cd backend
./mvnw verify
cd ..
docker compose --env-file .env -f infra/compose.yaml -f infra/compose.override.yaml up -d --build
```

Abrir http://localhost:8080. La configuración local sirve HTTP explícitamente y no requiere certificado. El `compose.override.yaml` también se carga automáticamente al ejecutar Compose desde `infra/`.

La SPA inicia Authorization Code con PKCE S256; Keycloak pide las credenciales.
La URI de retorno es `http://localhost:8080/callback`. No hay registro público ni selector de tenant.
El tenant se obtiene del token. Los refresh tokens los administra la biblioteca
OIDC en sessionStorage, sin copiar tokens a logs ni mostrarlos en pantalla.
El interceptor solo envía el bearer a `/api/v1/` del mismo origen.

Realm: `seed/tenants.yaml` alimenta usuarios, roles, clientes y tenant_id. El JSON
versionado tiene referencias `${DC_SEED_*}`; Keycloak las resuelve desde las
variables existentes. Las cuentas de servicio tienen API_CLIENT y el tenant de
su institución. Todos los clientes incluyen audiencia `doggycredit-api`. La API
valida issuer, firma y audiencia (Compose), exige un tenant UUID y convierte
roles de Keycloak a autoridades Spring. Las autorizaciones de negocio y RLS
pertenecen al trabajo de plataforma de Juanjo y no se dan por terminadas aquí.

La importación inicial no actualiza un realm existente. Si cambia la semilla,
coordinar actualización con Nicole mediante administración de Keycloak; no
borrar volúmenes automáticamente. Keycloak persiste en `keycloak_data` y usa
`start-dev` para esta demostración; no es un despliegue de producción.

## Contrato y generación

OpenAPI Generator **7.25.0**, `useSpringBoot4`, Jackson 3 e interfaces sin cuerpos
por defecto. Maven genera en `backend/target/generated-sources/openapi`; ejecutar
Maven desde `backend/` (el parser OpenAPI falla con rutas absolutas con espacios).
El controlador de consentimiento implementa `ConsentimientosApi` y usa sus DTO.
Las validaciones de dominio existentes se conservan. No se modificaron endpoints
ni esquemas del contrato. Caddy elimina `/api/v1` al pasar la petición al backend.

El cliente Angular 22 se regenera con `bash scripts/generate_clients.sh`; no editar
`web/src/app/api` a mano. El proveedor BASE_PATH configura el origen actual.
CI comprueba regeneración sin diferencias y oasdiff rechaza rupturas en PR.

## Pruebas

```bash
.venv/bin/python -m unittest discover -s scripts -p 'test_*.py'
cd web
npm ci
npm test -- --watch=false
npm run build
cd ../backend
./mvnw verify
cd ..
set -a
source .env
set +a
.venv/bin/python e2e/identity_smoke.py --ca /tmp/doggycredit-root.crt
```

La prueba HTTP usa Keycloak real: 401 sin token, client_credentials de ambos
tenants, login humano con PKCE, comprobación de state/nonce/tenant_id y uso de
cada token contra POST de consentimiento real (201). Crea solo consentimientos
sintéticos. El script decodifica claims para las aserciones; quien verifica
criptográficamente el access token es la API. No sustituye la revisión visual
del navegador: iniciar sesión, recargar, cerrar sesión y confirmar retorno.

El flujo completo de evaluación, auditoría y 404 entre tenants requiere los
módulos de los demás integrantes; no se simula su aprobación en estas pruebas.

## VM y despliegue por etiqueta

Nicole debe proveer una VM con Docker Compose, Python/venv, DNS apuntando a la VM,
443 accesible y `/srv/doggycredit/.env` con las variables ya declaradas. Usar
`DC_KEYCLOAK_ISSUER_URI=https://DOMINIO/auth/realms/doggycredit` y `DC_HTTP_PORT=443`.
El usuario de despliegue necesita permiso sobre `/srv/doggycredit` y Docker.

Configurar el environment GitHub `demo` con DEMO_HOST, DEMO_USER, DEMO_SSH_KEY y
DEMO_KNOWN_HOSTS (huella del servidor verificada por Nicole). Son credenciales de
CI, no nuevas variables de aplicación. Una etiqueta `v*` ejecuta primero el CI;
solo después copia un archivo del commit exacto por SSH con verificación de host.

El script de despliegue genera las URLs públicas, construye imágenes con el SHA,
conserva la release anterior y valida `/actuator/health` por HTTPS. Caddy obtiene
certificado público por TLS-ALPN en 443; para localhost utiliza CA interna.
Solo Caddy publica un puerto. La semilla no incluye contraseñas reales. Si falla
el arranque o la salud, se restauran imágenes anteriores sin borrar datos.
Las migraciones de base de datos no se revierten: deben ser compatibles con la
release anterior. `current` cambia únicamente tras la comprobación de salud.

No se ha ejecutado un despliegue remoto: no se proporcionaron VM, dominio ni
credenciales. SPIKE-6 necesita esa ejecución para cumplir el cierre de la guía.

## Evidencia local (28/09/2026)

- 4 pruebas del generador de realm aprobadas.
- 4 pruebas Angular aprobadas; compilación de TypeScript y plantillas aprobada.
- Build de producción Angular aprobado (347.24 kB antes de compresión).
- 16 pruebas backend de seguridad/unitarias/ArchUnit/Modulith aprobadas.
- Docker no disponible: las pruebas Testcontainers fallan por ausencia del daemon;
  no se omiten ni se consideran aprobadas. E2E real y VM pendientes de ejecución.
- En macOS local la caché LMDB de Angular abortó el proceso. La compilación pasa
  con `NG_BUILD_CACHE_STORE=sqlite npm run build`; no es una variable de la app,
  sino una opción de diagnóstico de la herramienta Angular.

Referencias de implementación: [Keycloak detrás de proxy](https://www.keycloak.org/server/reverseproxy),
[configuración OIDC Angular](https://www.angular-auth-oidc-client.com/docs/documentation/configuration),
[generador Spring](https://openapi-generator.tech/docs/generators/spring/).
