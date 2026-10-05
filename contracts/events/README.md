# `contracts/events/`

Esquemas JSON de los eventos de dominio publicados por el outbox de Spring
Modulith (`@Externalized`). Formato: `<NombreEvento>.v<N>.schema.json`, en
pasado (`CreditAssessmentCompleted`, no `CompleteCreditAssessment`).

| Evento | Lo publica | Cuándo | Record Java |
|---|---|---|---|
| `ConsentGranted.v1` | `consent` | al otorgar un consentimiento | `com.doggycredit.consent.ConsentGranted` |
| `ConsentRevoked.v1` | `consent` | al revocar un consentimiento, total o por fuente (B-07) | `com.doggycredit.consent.ConsentRevoked` |
| `CreditAssessmentCompleted.v1` | `assessment` | al terminar una evaluación (no en una repetición idempotente) | `com.doggycredit.assessment.CreditAssessmentCompleted` |

## Reglas

- **Sobre común:** todo evento lleva `eventId` (para deduplicar entregas
  repetidas), `ocurridoEn` y `tenantId`. El `tenantId` es también la clave
  con la que se externaliza (`@Externalized("doggycredit.<módulo>::#{tenantId()}")`).
- **Minimización:** ningún evento lleva el documento del titular ni datos
  personales en claro; se referencian por id (`consentId`, `applicantId`).
- **Nombres:** los mismos del recurso equivalente del OpenAPI (`estado`,
  `banda`, `razones`, `simulado`…).
- **Versionado:** un esquema publicado no se edita. Un campo nuevo, quitado o
  renombrado va en `v2` (archivo y record nuevos), y los consumidores
  migran a su ritmo.
- **Se publican en la misma transacción** que el cambio que describen
  (`ApplicationEventPublisher` dentro del método `@Transactional`): si la
  transacción se revierte, el evento no existe.

`DomainEventSchemaTests` serializa cada record y falla si no cumple su
esquema; `ConsentEventsTests` y `AssessmentEventsTests` prueban con
`Scenario` de Modulith que el evento se publica.

## Consumidores

- **Webhooks (E-08):** escuchan con `@ApplicationModuleListener` y entregan
  el JSON del evento tal cual, con `SCHEMA` (p. ej. `"ConsentGranted.v1"`)
  como tipo.
- **Sin broker todavía** (DEBT-002): la externalización queda declarada en
  cada record, pero hasta que haya un consumidor externo los eventos viven
  solo en el outbox de Modulith (`event_publication`).
