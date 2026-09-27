# `infra/keycloak/`

Esta carpeta se monta como `/opt/keycloak/data/import` en el contenedor de
Keycloak. Está vacía a propósito: generar `doggycredit-realm.json` desde
`seed/tenants.yaml` es el trabajo del **SPIKE-2** (Franco, con Nicole) del
Sprint 1 — ver el capítulo 9.5 de la guía de inducción.

Cuando el realm exista, `docker compose up` lo importa automáticamente al
arrancar Keycloak (no hace falta ningún paso manual adicional).

No se sube ningún archivo con secretos reales a esta carpeta ni al repositorio.
