# Pruebas E2E contra Compose

`identity_smoke.py` verifica PKCE, client credentials y claves de API. La suite
Playwright de **E-06 / LOS-105** agrega el recorrido real de la SPA en Chromium:

- Login Keycloak con PKCE S256 para administradores de Gratis y Pro.
- Alta del solicitante, consentimiento explícito, evaluación y perfil con score,
  fuentes consolidadas y recomendaciones según el plan.
- Revocación desde el perfil, rechazo de una evaluación posterior con
  `422 CONSENT_REQUIRED` y comprobación del evento de auditoría por correlation ID.
- Analytics básico en Gratis, analítica avanzada en Pro, límite de integraciones
  y autorización de Webhooks (`403 FEATURE_NOT_IN_PLAN` en Gratis, `200` en Pro).
- Aislamiento del mismo documento entre tenants en API y perfil; una cabecera
  `X-Tenant-Id` no permite sustituir el tenant del JWT.

Las pruebas usan la API y las fuentes WireMock incluidas en Compose. El helper
de captura reenvía una petición real y entrega su respuesta sin sustituir datos.
No modifican el cliente generado
en `web/src/app/api/`. Los IDs, usuarios y documento se derivan de
`seed/tenants.yaml`; el fixture ignorado contiene nombres de variables de
contraseña, nunca sus valores.

## Ejecución local

Se necesitan Docker/Compose, Python con `scripts/requirements.txt`, Node 22 y
Chromium de Playwright. Ejecutar desde la raíz del repositorio. El proyecto
`doggycredit-e2e` debe empezar con volúmenes nuevos: el flujo exige comprobar un
`POST /solicitantes` exitoso y no reutiliza un solicitante existente. Detener antes
otro Compose que ocupe los puertos 443, 8089–8091 o 3300.

```bash
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements.txt
npm ci --prefix e2e
(cd e2e && npx playwright install chromium)
.venv/bin/python scripts/generate_realm.py --origin https://localhost
.venv/bin/python scripts/generate_e2e_fixture.py
npm run typecheck --prefix e2e
npm run test:helpers --prefix e2e
npm run test:regression --prefix e2e
npm run test:list --prefix e2e

export COMPOSE_PROJECT_NAME=doggycredit-e2e
docker compose --env-file .env.example -f infra/compose.yaml up -d --build --wait --wait-timeout 300
docker compose --env-file .env.example -f infra/compose.yaml cp caddy:/data/caddy/pki/authorities/local/root.crt /tmp/doggycredit-e2e-root.crt
curl --fail --cacert /tmp/doggycredit-e2e-root.crt https://localhost/actuator/health

set -a
source .env.example
set +a
npm test --prefix e2e
```

El login usa las credenciales ficticias de `.env.example`, las mismas que importa
Keycloak. `E2E_BASE_URL` cambia el origen del navegador, cuyo issuer/realm debe
coincidir. La suite permite la CA local en el navegador; el paso de salud verifica
el certificado con la CA de Caddy. Un solo worker y cero reintentos mantienen
deterministas las mutaciones. No se guardan trazas, videos ni estado autenticado.

El helper registra una ruta antes de ejecutar la acción que genera la petición.
Usa [route.fetch()](https://playwright.dev/docs/api/class-route#route-fetch) para
obtener el status, las cabeceras y el cuerpo completos del servidor y luego
entrega esa misma respuesta al navegador con `route.fulfill()`. La copia de los
bytes vive en el proceso de pruebas: consultar `json()` después de navegar ya
no depende de `Network.getResponseBody` de Chromium. Cada captura se elimina al
terminar o fallar. No se repiten POST ni se siguen redirecciones en la captura;
las respuestas HTTP de error se conservan y las aserciones siguen exigiendo el
status y los datos reales.

`test:helpers` verifica espera del registro antes de la acción, cuerpo parcial,
filtrado por origen/ruta/método, cabeceras, errores, timeout y limpieza sin
Docker ni navegador. `test:regression` agrega cinco casos en Chromium contra un
servidor HTTP sintético: envía las cabeceras antes de terminar el cuerpo y la
página navega inmediatamente al recibirlas. Incluye GET, POST y un 403, y exige
que el servidor reciba una sola petición sin cambiar su método ni cuerpo.
Estas regresiones no sustituyen los cinco escenarios reales contra Compose.

Abrir `e2e/playwright-report/index.html` para revisar fallos. Al terminar, eliminar
**solo los volúmenes de este proyecto de pruebas**, para repetir desde cero:

```bash
COMPOSE_PROJECT_NAME=doggycredit-e2e docker compose --env-file .env.example -f infra/compose.yaml down --volumes --remove-orphans
```

## CI y aceptación de E-06

El job `e2e` en `.github/workflows/ci-cd.yml` corre en PRs, pushes a `main` y
ejecuciones manuales, después de backend, web y contratos. Instala Chromium con
sus dependencias Linux, ejecuta la regresión de navegación, levanta un Compose
propio con volúmenes nuevos, ejecuta la suite, conserva reportes y capturas de
fallos por 7 días y siempre elimina
sus contenedores y volúmenes. El despliegue requiere también este job en verde.

La rama `feature/LOS-105-e2e-flujo-completo` integra E-08 desde
`feature/LOS-103-webhooks`, incluida la corrección del lector de cabeceras de
WireMock. La pantalla de Desarrolladores consulta `GET /webhooks` y la prueba de
permisos verifica esa respuesta junto con el formulario vacío PRO o el aviso de
restricción FREE. Los avisos de consentimiento, analytics y Webhooks se buscan
por su rol accesible `status` y su título exacto, conforme a `ds-alert`.
E-07 conserva su job de carga independiente; estos cambios no alteran sus
fixtures ni resultados.

La evaluación publica `recomendaciones[]` de C-06 a partir del snapshot que ya
guarda el motor de elegibilidad. POST y GET aplican `RECOMENDACIONES` del PlanGate
al plan actual: FREE recibe una lista vacía y `recomendacionesDisponibles: false`.
El perfil carga la evaluación más reciente del solicitante y muestra su producto
y monto, el aviso de restricción devuelto por la API o un estado vacío real.
La prueba comprueba que el producto y monto visibles coinciden con el resultado
de la evaluación, además del permiso de cada plan.
Las comprobaciones siguen activas: un `404` de Webhooks, recomendaciones
ausentes o el mensaje estático no cuentan como éxito. Las comprobaciones suaves
de recomendaciones permiten terminar revocación y auditoría, pero hacen fallar
el test igualmente.

E-06 se considera terminada cuando el job pasa **en `main`**. Typecheck, discovery
y los tests del generador no sustituyen una ejecución contra Compose. Si no hay
acceso a Docker o Chromium, registrar esa limitación y mantener pendiente la
aceptación. Para revisar el generador de datos:

```bash
.venv/bin/python -m unittest discover -s scripts -p 'test_*.py'
```
