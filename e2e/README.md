# `e2e/`

Pruebas de extremo a extremo contra Compose completo (nunca contra dobles
en memoria — lección 1 de MedicalSys).

| Qué | Dónde | Job del CI |
|---|---|---|
| Login PKCE y `client_credentials` | `identity_smoke.py` | 4 · Login (e2e) |
| Flujo completo de la consola (E-06) | `tests/` (Playwright) | 6 · Flujo E2E |
| Prueba de carga (E-07) | `k6/` | ver `k6/README.md` |

## Flujo completo (E-06)

Playwright recorre la consola en Chromium contra Caddy + SPA + API + Keycloak
+ simuladores, con el login real de Keycloak y sin mocks de la API:

- `credit-flow.spec.ts`, para el tenant FREE y el PRO de `seed/tenants.yaml`:
  login → `/app/evaluaciones/nueva` (alta, consentimiento, fuentes, evaluación)
  → `/app/clientes/:id` (Perfil 360, score, fuentes) →
  revocar el consentimiento → `/app/configuracion#seguridad` (auditoría). En
  PRO además conecta banco y billetera y verifica que el perfil los consolida.
- `plan-gates.spec.ts`: el tenant FREE ve bloqueadas la analítica avanzada,
  una segunda integración (`PLAN_LIMIT_REACHED`) y los webhooks
  (`FEATURE_NOT_IN_PLAN`); el PRO las tiene disponibles.

Cada escenario falla también si la API respondió 5xx o 429 en algún momento.

## Flujos del Sprint 3 (E-12)

- `manual-review.spec.ts` (RF-DEC-03): el analista evalúa a un titular moroso, que
  queda en revisión manual. Desde la bandeja comprueba que no se puede enviar sin
  justificación, resuelve con override justificado y producto, y el caso sale de
  la bandeja. Después el admin encuentra el override en la auditoría por su
  `X-Correlation-Id`.
- `plan-upgrade.spec.ts` (RF-PLN-04): el operador (`PLATFORM_ADMIN`) pasa el
  tenant Gratis a Pro. En la sesión ya abierta del admin, las funciones Pro
  (analítica avanzada y webhooks) se habilitan en ≤ 1 min sin volver a iniciar
  sesión. Al terminar se restaura el plan Gratis, también si el escenario falla.

Los dos esperan historias de otros: C-10 (backend de la bandeja) y A-11 (operador
sembrado). Mientras tanto quedan en `test.fixme` (el CI los informa como
omitidos). Cada historia los activa al fusionarse; ver
[e2e-flujos-sprint3.md](../docs/runbooks/e2e-flujos-sprint3.md). Para correrlos
antes en local: `E2E_DEPENDENCIAS=bandeja,cambioDePlan npx playwright test`.

### Ejecutar en local

Desde la raíz del repositorio (usa volúmenes propios, no toca los de su
Compose de desarrollo; el puerto 443 debe estar libre):

```bash
python scripts/generate_realm.py
docker compose --env-file .env.example -f infra/compose.yaml -f e2e/compose.e2e.yaml \
  -p doggycredit-e2e up -d --build --wait

cd e2e
npm ci
npx playwright install chromium
set -a; source ../.env.example; set +a   # contraseñas de los usuarios de demo
npx playwright test

cd .. && docker compose --env-file .env.example -f infra/compose.yaml -f e2e/compose.e2e.yaml \
  -p doggycredit-e2e down -v
```

Los escenarios se pueden repetir sobre la misma base: reutilizan el
solicitante y las integraciones que ya existan. `npx playwright test --ui`
abre el modo interactivo; en el CI, si algo falla, el job sube el reporte
HTML, las capturas y la traza como artefacto `playwright-report`.

`compose.e2e.yaml` solo sube el límite de solicitudes por minuto del plan
FREE (A-07): los escenarios usan la consola de ese tenant varias veces en el
mismo minuto. Las demás restricciones del plan no cambian.
