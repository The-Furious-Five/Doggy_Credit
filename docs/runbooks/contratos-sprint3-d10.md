# Contratos del Sprint 3 (D-10 / LOS-110)

Contract-first para DEC-03, PLN-04/05 y CON-03. Las rutas nuevas van en **tags
propios** (`planes`, `reporte-accesos`), como `revision-manual`: con
`skipDefaultInterface`, sumar operaciones a `TenantApi`, `AdminApi` o
`AuditoriaApi` rompería la compilación de sus controladores. Así el backend
compila igual y las rutas responden 404 hasta que alguien implemente la interfaz.

| Historia | Operación | Ruta | Implementa |
| --- | --- | --- | --- |
| RF-DEC-03 | `listarCasosRevision` | `GET /revision-manual` | C-10 (LOS-111) |
| RF-DEC-03 | `resolverCasoRevision` | `POST /revision-manual/{evaluacionId}/resolucion` | C-10 (LOS-111) |
| RF-PLN-04 | `actualizarTenant` | `PATCH /admin/tenants/{id}` (ya existe) | A-11 (LOS-115) |
| RF-PLN-04/05 | `obtenerEstadoPlan` | `GET /tenant/actual/plan` | A-12 (LOS-116) |
| RF-PLN-05 | `programarBajadaPlan` | `PUT /tenant/actual/plan/bajada` | A-12 (LOS-116) |
| RF-PLN-05 | `cancelarBajadaPlan` | `DELETE /tenant/actual/plan/bajada` | A-12 (LOS-116) |
| RF-CON-03 | `obtenerReporteAccesos` | `GET /auditoria/accesos` | C-11 (LOS-112) |

Código de error nuevo: `PLAN_CHANGE_NOT_ALLOWED` (409), en `catalog.yaml` y en
`ErrorCode`. Lo demás usa códigos existentes.

## DEC-03: revisión manual

Se adopta sin cambios la propuesta de E-10 (ver
[bandeja-revision-e10.md](bandeja-revision-e10.md)). C-10 implementa
`RevisionManualApi` y pone `bandeja: true` en `e2e/tests/support.ts`.

## PLN-04: subir de plan

No hace falta una ruta nueva: `PATCH /admin/tenants/{id}` ya cambia el plan y
el cambio se nota en ≤ 1 min (comprobado el 2026-10-09, ver
[e2e-flujos-sprint3.md](e2e-flujos-sprint3.md)). El contrato ahora fija dos
reglas que A-11 debe cumplir:

- subir cancela una bajada programada y termina la gracia vigente;
- todo cambio de plan queda en `audit.access_log`.

Este `PATCH` es inmediato en ambos sentidos: es la herramienta del operador y
la que usa el E2E para devolver el tenant a Gratis.

## PLN-05: bajar al cierre del período

- El **administrador del tenant** programa la bajada. No requiere pago, así que
  no espera al operador (DEBT-010 solo frena el autoservicio de la subida).
- **Período** = mes calendario en UTC, el mismo de `UsageMeterImpl`. La bajada
  se aplica en `periodo.fin`.
- Al aplicarse empiezan **30 días de gracia**: las integraciones que excedan el
  plan nuevo quedan desactivadas, no borradas; la preaprobación automática vuelve
  a decisión sugerida; nada se purga antes de `gracia.hasta`.
- `PUT` reemplaza una bajada ya programada. `DELETE` es idempotente y no corta
  una gracia en curso.
- `planDestino` igual o superior al plan actual → 409 `PLAN_CHANGE_NOT_ALLOWED`.

## CON-03: reporte de accesos

- `GET /auditoria/accesos` filtra por `solicitanteId`, por `correlationId` o por
  ambos. Sin ninguno → 400 `VALIDATION_ERROR`. Solicitante de otro tenant → 404
  `UNKNOWN_APPLICANT`. Solo `TENANT_ADMIN`.
- Cada fila es un `RegistroAuditoria` más `solicitanteId` y `consentimientoId`.
  `autorizaciones` resume los consentimientos de la página sin el documento del
  titular.
- Lo que C-11 necesita y hoy no existe: `audit.access_log` no guarda el titular ni
  el consentimiento. Hay que agregarlos (columnas nuevas y un `record` extendido en
  `AuditTrail`) o resolverlos por la API pública de cada módulo, nunca con un JOIN
  entre esquemas. Las lecturas del perfil todavía no se auditan.

## Verificación de este PR

- `bash scripts/generate_clients.sh`: el contrato valida y el cliente Angular
  queda regenerado en `web/src/app/api/`.
- `./mvnw -o -DskipTests compile` en `backend/`: compila con `PlanesApi` y
  `ReporteAccesosApi` generadas y sin implementar.
- `npm run build` en `web/`.
- oasdiff: lo corre el job de contratos del CI sobre el PR. Solo se agregan rutas,
  tags y esquemas, y cambia la descripción de `actualizarTenant`.
