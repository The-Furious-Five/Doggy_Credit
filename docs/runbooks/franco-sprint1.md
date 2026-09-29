# Entrega Franco — Sprint 1

Alcance: §9.5 de DoggyCredit_Guia_Induccion_Sprint1.pdf: SPIKE-2, SPIKE-5,
SPIKE-6 y HAB-05. Analytics y k6 son responsabilidades futuras, sin tarea
implementable asignada en este sprint. No se implementan las historias de
los otros integrantes. El Excel y el documento normativo de arquitectura
referenciados por el PDF no se encuentran en este checkout.

## Preparar y arrancar

Requiere Docker Compose, Java 21, Node 22.22.3 o superior compatible con
Angular 22, y Python 3.12+. Usar el `.env` entregado por Nicole. No generar
nuevos secretos. El issuer local es `https://localhost/auth/realms/doggycredit`
(guía §9.5, HAB-05) y Caddy publica el puerto 443 (`DC_HTTP_PORT`).

```bash
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements.txt
.venv/bin/python scripts/generate_realm.py
npm --prefix web run generate:api
cd backend
./mvnw verify
cd ..
docker compose --env-file .env -f infra/compose.yaml up -d --build --wait
```

Abrir https://localhost.

### Certificado local

Caddy firma `localhost` con su CA interna, que persiste en el volumen
`caddy_data`. Se confía una sola vez por máquina (no es un secreto: es la CA
pública de ese volumen local):

```bash
docker compose --env-file .env -f infra/compose.yaml cp \
  caddy:/data/caddy/pki/authorities/local/root.crt /tmp/doggycredit-root.crt
# macOS
sudo security add-trusted-cert -d -r trustRoot \
  -k /Library/Keychains/System.keychain /tmp/doggycredit-root.crt
# Linux (Debian/Ubuntu)
sudo cp /tmp/doggycredit-root.crt /usr/local/share/ca-certificates/doggycredit-root.crt
sudo update-ca-certificates
```

Reiniciar el navegador después de confiar en la CA. Si se borra `caddy_data`,
Caddy genera otra CA y hay que repetir el paso.

### Por qué el botón "Iniciar sesión" lleva a otra página

Es el comportamiento exigido: Keycloak es el único servidor de identidad y
"nadie del equipo construye login propio" (guía §2.5). La SPA nunca recibe
contraseñas: el botón inicia Authorization Code + PKCE (§5.1), Keycloak muestra
su formulario en `/auth/...` (mismo dominio, en español y con el nombre
DoggyCredit) y devuelve a `/callback` con el código. Un formulario de usuario y
contraseña dentro de Angular exigiría el *password grant*, que está deshabilitado
a propósito en todos los clientes.

La SPA inicia Authorization Code con PKCE S256; Keycloak pide las credenciales.
La URI de retorno es `https://localhost/callback`. No hay registro público ni selector de tenant.
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
borrar volúmenes automáticamente. Quien importó el realm cuando el entorno local
usaba `http://localhost:8080` conserva esas URIs de retorno: con acuerdo del
equipo, recrear solo `doggycredit_keycloak_data` (contiene únicamente el realm
importado de la semilla). Keycloak persiste en `keycloak_data` y usa
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
npm run ds:check
npm run ds:test
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

## Evidencia local (29/09/2026)

- Compose completo en HTTPS (`https://localhost`, puerto 443): los 6 servicios
  en `healthy`/running, CA de Caddy verificada por `identity_smoke.py --ca`.
- `identity_smoke.py`: 401 sin token, client_credentials de ambos tenants con
  `tenant_id` correcto, PKCE S256 humano y POST real de consentimiento (201).
- Issuer publicado: `https://localhost/auth/realms/doggycredit`. Pantalla de
  Keycloak en español ("Inicia sesión en DoggyCredit").
- `configure_demo.py` con un dominio de prueba: Caddyfile sin `tls internal`
  (antes quedaba y la VM habría servido un certificado no confiable) y
  `caddy validate` aprobado.
- Backend `./mvnw clean verify`: 35 pruebas aprobadas, 0 omitidas (incluye
  Testcontainers). Web: 15 pruebas, `ds:check`, `ds:test` y build aprobados.
  Scripts: 10 pruebas aprobadas. Despliegue remoto: sigue pendiente de la VM.

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
