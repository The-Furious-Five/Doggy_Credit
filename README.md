# DoggyCredit

Plataforma SaaS multi-tenant de inteligencia crediticia. Los 5 furiosos.

Este repositorio es el código; **no** duplica el informe de arquitectura,
el Product Backlog ni la guía de inducción — esos viven un nivel arriba, en
`../DoggyCredit/`:

- [`../DoggyCredit/DoggyCredit_Guia_Induccion_Sprint1.pdf`](../DoggyCredit/DoggyCredit_Guia_Induccion_Sprint1.pdf) — **empiecen por aquí**.
- `../DoggyCredit/DoggyCredit_Backlogs.xlsx` — Product Backlog y Sprint 1 Backlog (fuente de verdad de horas y estado).
- `../DoggyCredit/mesa_de_diseno/` — el documento de arquitectura completo y las 9 posiciones de la mesa de diseño.

## Estado (28 de septiembre de 2026)

Trabajo de Nicole (Dev4, Technical Lead / Scrum Master) previo y del Día 1
del Sprint 1 (`HAB-01`, `HAB-07`, `SPIKE-3`):

- ✅ Estructura del monorepo, `.gitignore`, `.env.example`.
- ✅ `seed/tenants.yaml` — única fuente de tenants, usuarios e IDs de demo.
- ✅ `contracts/openapi/doggycredit-api.v1.yaml` v0 (paths de consentimientos y evaluaciones).
- ✅ `contracts/errors/catalog.yaml` — catálogo único de códigos de error.
- ✅ `contracts/canonical/perfil-financiero.v1.schema.json` — Perfil Financiero 360 v1.
- ✅ `infra/compose.yaml` — Postgres 17, Keycloak, WireMock (buró), Grafana LGTM.
- ✅ `backend/` — esqueleto Spring Boot 4.1.1 + Spring Modulith 2.1.1, con los 11 módulos como paquetes y `ApplicationModulesTest` verificando sus fronteras desde ya.
- ✅ `web/` — esqueleto Angular 22.2, compila (`angular-auth-oidc-client` ya instalado para `SPIKE-2`/`HAB-05`).
- ✅ **`SPIKE-3`** — `ml/`: dataset sintético → regresión logística → ONNX, con todos los gates en verde (AUC, KS, equidad, monotonía, reproducibilidad). Servido dentro de la JVM por `com.doggycredit.scoring.internal.OnnxScoringAdapter`; `OnnxParityTest` confirma paridad Python↔Java (20/20, tolerancia 1e-6). Detalle en `ml/README.md`.
- ⬜ Todo lo demás: **tareas del equipo**, ver el capítulo 9 de la guía de inducción (`SPIKE-1`, `SPIKE-2`, `SPIKE-4` a `SPIKE-6`, `HAB-02` a `HAB-05`, y las historias `RF-*`).

## Arranque rápido

```bash
cp .env.example .env   # o pidan el .env real a Nicole
docker compose --env-file .env -f infra/compose.yaml up -d --build
docker compose --env-file .env -f infra/compose.yaml ps   # todos "healthy"

cd backend && ./mvnw test   # incluye ApplicationModulesTest (FF-01/FF-02)
```

El CI (`.github/workflows/ci.yml`) exige Docker, corre ArchUnit + Spring
Modulith + tests y falla ante cualquier test omitido que no esté en la lista
blanca con vencimiento: ver [`docs/runbooks/tests-omitidos.md`](docs/runbooks/tests-omitidos.md).

## Estructura

```
doggycredit-platform/
├── contracts/       # OpenAPI, catálogo de errores, eventos, modelo canónico — fuente de verdad
├── seed/            # tenants.yaml — única fuente de IDs de demo
├── backend/         # doggycredit-api: Spring Boot + Modulith, un proceso, 11 módulos
├── ml/              # SPIKE-3: dataset sintético, entrenamiento, ONNX (Nicole)
├── web/             # SPA Angular 22 (Franco)
├── simulators/      # WireMock: buró (Sprint 1), banco y billetera (Sprint 2)
├── infra/           # docker-compose, Keycloak, Caddy
├── e2e/             # pruebas de extremo a extremo contra Compose completo
├── docs/            # ADR nuevos, C4 generado, runbooks, registro de deuda
└── .github/workflows/
```

## Convenciones

Ver el capítulo 6 de la guía de inducción: código en inglés, documentación
en español, un esquema por módulo, `DC_<MODULO>_<CLAVE>` para variables de
entorno, roles idénticos en Keycloak/backend/UI (`TENANT_ADMIN`, `ANALYST`,
`API_CLIENT`, `PLATFORM_ADMIN`).

**Regla que no se negocia:** el `tenant_id` sale siempre y únicamente del
token JWT. Nunca de una cabecera, un parámetro de ruta o el cuerpo de una
petición.

## Entrega Franco — Sprint 1

Login Angular con PKCE, realm multi-tenant generado, clientes OpenAPI y Compose
con Caddy HTTPS implementados. Ver [instrucciones y evidencia de verificación](docs/runbooks/franco-sprint1.md).
El estado histórico de inicialización arriba corresponde al 25/09; la integración
real requiere Docker y el `.env` de Nicole. El despliegue remoto requiere la VM.

## Acceso local

Caddy es la entrada única en HTTPS: [https://localhost](https://localhost)
(SPA, `/auth` de Keycloak y `/api/v1`). El certificado lo emite la CA interna de
Caddy; confíen en ella una sola vez siguiendo el
[runbook](docs/runbooks/franco-sprint1.md#certificado-local). Grafana queda en
`http://localhost:3300` y el simulador del buró en `http://localhost:8089`.

## Design System

Toda interfaz debe consumir el [Design System de DoggyCredit](docs/design-system/README.md).
Catálogo interactivo: `/design-system`. El login ya lo utiliza; los scripts de
build y CI verifican controles, tokens e imports. Reglas de contribución para
asistentes y desarrolladores en `AGENTS.md`.
