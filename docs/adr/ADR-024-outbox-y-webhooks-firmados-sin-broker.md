# ADR-024 · Outbox y webhooks firmados, sin broker de mensajes

Fecha: 2026-10-20. Estado: aceptado (D-03, E-08; construido).

## Contexto

Los clientes necesitan enterarse de `ConsentGranted.v1`, `ConsentRevoked.v1` y
`CreditAssessmentCompleted.v1` (payloads en `contracts/events/`) sin que la
evaluación dependa de la disponibilidad de su receptor.

## Decisión

- Los eventos se publican dentro de la transacción de negocio con Spring
  Modulith (`@Externalized`; tabla `event_publication`, migración
  `V2026_09_29_1402`). Es el patrón outbox: `SynchronousAssessmentService`
  publica en la misma transacción que la evaluación.
- El módulo `webhooks` guarda la entrega en `webhooks.deliveries` (secreto y
  payload cifrados por tenant). `WebhookWorker` sondea cada 5 s por tenant
  activo; `WebhookService` toma una entrega con `FOR UPDATE SKIP LOCKED` y
  reintenta hasta `MAX_ATTEMPTS = 5`. Una tarea cada minuto reenvía
  publicaciones incompletas del outbox.
- Cada `POST` lleva `X-DoggyCredit-Event`, `-Event-Id`, `-Timestamp` y
  `-Signature` (HMAC-SHA256 con el secreto del tenant, `WebhookHttp`).
  Solo URL HTTPS pública; configuración en `/webhooks` (`TENANT_ADMIN`, plan PRO o superior).

## Alternativas consideradas

- Kafka o RabbitMQ: descartado; un servicio más que operar en un VPS para un volumen bajo.
- Envío directo dentro de la transacción de negocio: descartado; un receptor lento o caído bloquearía la evaluación.
- Sin firma: descartado; el receptor no podría autenticar el origen.

## Consecuencias

Entrega al menos una vez: los receptores deben deduplicar por `Event-Id`.
Latencia de segundos, no de milisegundos. Si crecen el volumen o los
consumidores, el outbox permite cambiar el transporte sin tocar el negocio.
Ver `docs/runbooks/webhooks-e08.md`.
