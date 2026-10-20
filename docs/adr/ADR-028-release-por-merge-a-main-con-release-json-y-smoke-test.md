# ADR-028 · Release por merge a `main`, con `/release.json` y smoke test

Fecha: 2026-10-20. Estado: aceptado (E-13; construido).

## Contexto

La Sprint Review se hace sobre el VPS. Hacía falta saber qué versión está en línea
y evitar despliegues manuales desde un laptop.

## Decisión

`.github/workflows/ci-cd.yml` corre seis revisiones (backend, web, contratos,
identidad, ml, e2e) en cada PR y push a `main`; el job `7 · Desplegar a
producción (VPS)` solo corre en `main` (push, manual o programado) con todo en
verde y `concurrency: production-deploy`. Despliega imágenes de GHCR con
`infra/compose.yaml` + `infra/compose.prod.yaml`. La imagen web publica el SHA en
`/release.json` (`Cache-Control: no-store` en `infra/caddy/Caddyfile`).
`scripts/smoke_release.py` verifica desde Internet salud, SHA, SPA, issuer OIDC,
401 sin token, `/actuator/env` 404 y HSTS; el job falla si algo no cumple. El
job 6 lo ejecuta contra el Compose de cada PR. Ver `docs/runbooks/release-sprint.md`.

## Alternativas consideradas

- Despliegue manual por SSH: descartado; no reproducible ni auditable.
- Etiquetas de versión por sprint: descartado; el SHA de `main` basta y es exacto.
- Comprobar solo `/actuator/health`: descartado; no distingue la versión nueva de la anterior.

## Consecuencias

Cada push a `main` reinicia la API, así que durante una review se congela `main`.
La verificación es de solo lectura (GET) y no consume cuota. No hay rollback
automático: se revierte con otro merge.
