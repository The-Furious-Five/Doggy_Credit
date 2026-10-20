# Vistas C4: contexto y contenedores (estado construido)

Actualiza las vistas del informe de arquitectura v1 (§9.4–9.5) con lo que existe
en el repositorio al 2026-10-20. Los contenedores coinciden con los servicios de
`infra/compose.yaml` + `infra/compose.prod.yaml`; el dominio público es
`https://doggycredit.los5furiosos.cloud`.

## Nivel 1: contexto

```mermaid
flowchart LR
  analista["Analista de crédito<br/>(TENANT_USER / TENANT_ADMIN)"]
  operador["Operador de plataforma<br/>(PLATFORM_ADMIN)"]
  integrador["Sistema del cliente<br/>(API key, M2M)"]
  receptor["Receptor de webhooks<br/>del cliente (HTTPS)"]

  dc["DoggyCredit<br/>evaluación crediticia multi-tenant"]

  buro["Buró de crédito (XML)"]
  banco["Core bancario<br/>(proveedor uno o dos)"]
  billetera["Billetera móvil"]

  analista -- "web SPA, app iOS, app Android" --> dc
  operador -- "web SPA" --> dc
  integrador -- "API REST /api/v1" --> dc
  dc -- "webhooks firmados HMAC" --> receptor
  dc -- "consulta con consentimiento" --> buro
  dc -- "consulta con consentimiento" --> banco
  dc -- "consulta con consentimiento" --> billetera
```

Las tres fuentes externas están simuladas con WireMock en todos los entornos,
incluido el VPS (`contracts/connectors/README.md`); las respuestas se marcan
`simulado` y las apps muestran «Datos simulados».

## Nivel 2: contenedores

```mermaid
flowchart TB
  web["Navegador<br/>SPA Angular"]
  ios["App iOS (SwiftUI)<br/>cliente Keycloak doggycredit-mobile"]
  and["App Android (Compose)<br/>cliente Keycloak doggycredit-mobile"]
  ext["Sistemas M2M<br/>y receptor de webhooks"]
  ops["Operador<br/>ssh -L 3300"]

  subgraph vps["VPS: Docker Compose (infra/compose.prod.yaml)"]
    caddy["caddy<br/>TLS, estáticos SPA, /release.json,<br/>enrutado /auth y /api/v1"]
    api["doggycredit-api<br/>Spring Boot + Spring Modulith"]
    kc["keycloak 26.4<br/>realm doggycredit"]
    pg[("postgres 17<br/>RLS por tenant")]
    lgtm["otel-lgtm<br/>Grafana, Loki, Tempo, Mimir<br/>(solo 127.0.0.1:3300)"]
    wb["wiremock-bureau"]
    wk1["wiremock-bank"]
    wk2["wiremock-bank-dos<br/>(B-10, inactivo salvo provider=dos)"]
    ww["wiremock-wallet"]
  end

  web -- "HTTPS 443" --> caddy
  ios -- "HTTPS, OIDC PKCE" --> caddy
  and -- "HTTPS, OIDC PKCE" --> caddy
  ext -- "HTTPS /api/v1" --> caddy
  caddy -- "/auth/*" --> kc
  caddy -- "/api/v1/*, /actuator/health" --> api
  api -- "JWKS y admin de clientes API" --> kc
  api -- "JDBC: rol doggycredit_app" --> pg
  kc --> pg
  api -- "OTLP métricas y trazas" --> lgtm
  api -- "REST + API key" --> wb
  api -- "REST + API key" --> wk1
  api -. "alternativo" .-> wk2
  api -- "REST + API key" --> ww
  api -- "POST firmado" --> ext
  ops -.-> lgtm
```

Notas verificables en los archivos:

- Solo `caddy` publica el puerto 443 (`infra/compose.yaml`); en producción los
  simuladores no se publican al host (`ports: !reset []`) y Grafana va solo a
  `127.0.0.1:3300`.
- Caddy nunca autoriza: borra `X-Tenant-*` entrante y el tenant sale del token
  (`infra/caddy/Caddyfile`). `/actuator/*` distinto de `health` responde 404.
- La API valida JWT con las claves de `keycloak:8080` por la red interna y el
  issuer público; entra a PostgreSQL con `doggycredit_app` (ADR-020), no con el
  administrador de Flyway.
- Postgres y Keycloak son servicios de `infra/compose.yaml` con
  `restart: unless-stopped` en producción.
- La imagen `caddy` lleva el build de la SPA (`web/Dockerfile`); en producción
  se usa `DC_WEB_IMAGE` de GHCR, y la API `DC_API_IMAGE`.
- Los diagramas de módulos internos de la API están en
  [modulith/](modulith/README.md).

## Módulos de la API (nivel 3, resumen)

Paquetes de `backend/src/main/java/com/doggycredit/`: `tenancy`, `consent`,
`customer`, `integration`, `scoring`, `assessment`, `recommendation`, `billing`,
`analytics`, `audit`, `webhooks` y `shared`. Cada uno expone su API en la raíz
del paquete y esconde su implementación en `internal/`; `ApplicationModulesTest`
rompe el build ante dependencias indebidas o ciclos.
