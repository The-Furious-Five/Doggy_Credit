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

Las pruebas usan la API y las fuentes WireMock incluidas en Compose, sin
interceptar peticiones ni sustituir respuestas. No modifican el cliente generado
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

Abrir `e2e/playwright-report/index.html` para revisar fallos. Al terminar, eliminar
**solo los volúmenes de este proyecto de pruebas**, para repetir desde cero:

```bash
COMPOSE_PROJECT_NAME=doggycredit-e2e docker compose --env-file .env.example -f infra/compose.yaml down --volumes --remove-orphans
```

## CI y aceptación de E-06

El job `e2e` en `.github/workflows/ci-cd.yml` corre en PRs, pushes a `main` y
ejecuciones manuales, después de backend, web y contratos. Instala Chromium con
sus dependencias Linux, levanta un Compose propio con volúmenes nuevos, ejecuta
la suite, conserva reportes y capturas de fallos por 7 días y siempre elimina
sus contenedores y volúmenes. El despliegue requiere también este job en verde.

**Dependencias pendientes al preparar esta rama:** el usuario confirmó que E-08
(Webhooks) y E-09 aún no están integradas. Además, el checkout disponible muestra
la pestaña Recomendaciones estática y no publica `recomendaciones[]` de C-06 en
la evaluación. Esas integraciones deben llegar a la base del PR antes de esperar
el flujo completo en verde. Las comprobaciones siguen activas: un `404` de
Webhooks, recomendaciones ausentes o el mensaje estático no cuentan como éxito.
Las comprobaciones suaves de recomendaciones permiten terminar revocación y
auditoría, pero hacen fallar el test igualmente.

E-06 se considera terminada cuando el job pasa **en `main`**. Typecheck, discovery
y los tests del generador no sustituyen una ejecución contra Compose. Si no hay
acceso a Docker o Chromium, registrar esa limitación y mantener pendiente la
aceptación. Para revisar el generador de datos:

```bash
.venv/bin/python -m unittest discover -s scripts -p 'test_*.py'
```
