# DoggyCredit

Plataforma SaaS multi-tenant de inteligencia crediticia. Los 5 furiosos.

## Estado final del MVP

Derivado de lo que hay en `main`, `docs/` y `contracts/` (no de la intención).
Tres categorías: **construido** (código en `main`, con pruebas), **simulado**
(funciona, pero contra datos o servicios de mentira) y **solo diseñado**
(contrato o informe, sin implementación). Los RF citados son los que aparecen en
contratos, código y runbooks; el catálogo completo de RF vive en el informe de
arquitectura, fuera de este repositorio.

| Estado | Qué | RF / referencia |
| --- | --- | --- |
| Construido | Consentimiento, Perfil Financiero 360, score ONNX explicable dentro de la JVM, decisión y bandeja de revisión manual con override auditado | RF-CON-01/03, RF-PER-01/06, RF-SCO-01/02/03, RF-DEC-03 |
| Construido | Elegibilidad de productos en todos los planes y ranking con monto y plazo en Pro | RF-REC-02/03, RF-NEC-01 |
| Construido | Planes Gratis/Pro, cuota mensual idempotente, gates por plan, cambio de plan, suspensión de tenants, administración por API | RF-PLN-04/05/06, RF-TEN-04 |
| Construido | Aislamiento por RLS, Keycloak multi-tenant con PKCE, MFA (TOTP) para `TENANT_ADMIN`, claves de API, webhooks firmados, auditoría encadenada, enmascarado en el servidor | RF-SEG-01/03/05/06, RF-API-02, RF-INT-07 |
| Construido | Analítica por tenant, resumen y avisos de cuota; consola web Angular y apps móviles iOS y Android; despliegue CI/CD a la VM con smoke test | RF-ANA-01/04 |
| Simulado | Buró, banco (dos proveedores) y billetera: WireMock en `simulators/`, también en la VM. El consentimiento no tiene validez legal verificada (DEBT-004) | RF-INT-02 |
| Simulado | Modelo de scoring entrenado con datos sintéticos (`ml/`, DEBT-005); pago del cambio de plan (sin facturación real) | RF-SCO-*, RF-PLN-04 |
| Solo diseñado | `GET /operador/metricas` (en el contrato OpenAPI, sin implementación en el backend) y los KPIs de cartera `tasaOverride` de analítica Pro (campo del contrato, no calculado por el backend) | RF-ANA-02 |
| Solo diseñado | Facturación real y autoservicio de registro (DEBT-010), broker de eventos (DEBT-002), KMS y rotación de claves (DEBT-006), alta disponibilidad (DEBT-001) | [registro de deuda](docs/debt-register.md) |

Deuda aceptada y su disparador de pago: [`docs/debt-register.md`](docs/debt-register.md).

## Arrancar

Requiere solo Docker y Git. Guía paso a paso (clonar, `.env`, `docker compose`,
CA de Caddy, login con MFA y una evaluación completa):
[`docs/runbooks/arranque-maquina-limpia.md`](docs/runbooks/arranque-maquina-limpia.md).

En corto:

```bash
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d --build --wait
# confiar en la CA de Caddy (runbook) y abrir https://localhost

cd backend && ./mvnw test   # opcional: pruebas, incluye ApplicationModulesTest
```

## Integrar con la API

Para crear evaluaciones desde otro sistema con una clave de API (token
`client_credentials`, `Idempotency-Key`, errores RFC 9457):
[`docs/guia-inicio-rapido-api.md`](docs/guia-inicio-rapido-api.md). El contrato completo
es [`contracts/openapi/doggycredit-api.v1.yaml`](contracts/openapi/doggycredit-api.v1.yaml).

El CI/CD (`.github/workflows/ci-cd.yml`, despliegue en [`docs/runbooks/despliegue-vps.md`](docs/runbooks/despliegue-vps.md)) exige Docker, corre ArchUnit + Spring
Modulith + tests y falla ante cualquier test omitido que no esté en la lista
blanca con vencimiento: ver [`docs/runbooks/tests-omitidos.md`](docs/runbooks/tests-omitidos.md).

## Estructura

```
Doggy_Credit/
├── contracts/           # OpenAPI, catálogo de errores, eventos, conectores, modelo canónico — fuente de verdad
├── seed/                # tenants.yaml — única fuente de IDs de demo; generador sintético
├── backend/             # doggycredit-api: Spring Boot + Modulith, un proceso, 11 módulos
├── ml/                  # dataset sintético, entrenamiento, ONNX
├── web/                 # SPA Angular 22 y Design System
├── ios-mobile-app/      # app nativa SwiftUI (iOS 18+)
├── android-mobile-app/  # app nativa Kotlin + Jetpack Compose (Android 8+)
├── simulators/          # WireMock: buró, banco (x2) y billetera
├── infra/               # compose.yaml, compose.prod.yaml, Keycloak, Caddy, Grafana
├── e2e/                 # Playwright, k6 y humo de identidad contra Compose completo
├── scripts/             # realm, clientes OpenAPI, despliegue, smoke de release
├── ci/                  # verificaciones del pipeline
├── docs/                # runbooks, ADR, C4, deuda, Design System
└── .github/workflows/
```

## Documentación

- [Runbooks](docs/runbooks/README.md): arranque, despliegue, release, operación de cada historia.
- [ADR](docs/adr/): decisiones de arquitectura nuevas (los ADR-001 a 018 están en el informe de arquitectura).
- [Vistas C4](docs/c4/): contexto y contenedores.
- [Registro de deuda técnica](docs/debt-register.md).
- [Design System](docs/design-system/README.md) · [Guion de la presentación final](docs/presentacion-final/guion.md).
- Apps móviles: [iOS](ios-mobile-app/README.md) · [Android](android-mobile-app/README.md).

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
El despliegue remoto está descrito en [despliegue-vps.md](docs/runbooks/despliegue-vps.md).

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
