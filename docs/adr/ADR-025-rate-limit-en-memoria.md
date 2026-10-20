# ADR-025 · Límite de tasa por tenant en memoria

Fecha: 2026-10-20. Estado: aceptado (A-07, DEBT-003; construido).

## Contexto

Cada plan define solicitudes por minuto. La API se despliega como una sola
instancia (`doggycredit-api` en `infra/compose.prod.yaml`).

## Decisión

`TenantRateLimiter` (Bucket4j) mantiene un bucket por tenant en un
`ConcurrentHashMap`, con ventana fija de un minuto (se rellena entero), y vuelve a leer
el plan cada minuto. `RateLimitInterceptor` responde `429 RATE_LIMITED` con
`Retry-After` y envía `X-RateLimit-Remaining` en las respuestas aceptadas; las
rutas sin tenant (`/actuator/health`) no se limitan. Es independiente de la
cuota mensual.

## Alternativas consideradas

- Bucket4j con Redis o JDBC: descartado; infraestructura extra sin segunda instancia.
- Límite en Caddy: descartado; no conoce el tenant ni el plan (el tenant sale del token).
- Ventana deslizante: descartado; la ventana fija coincide con lo que muestra la consola.

## Consecuencias

Los contadores se pierden al reiniciar y no se comparten: con N réplicas el límite
efectivo sería N veces mayor. Antes de escalar horizontalmente hay que pasar a
un almacén compartido (DEBT-003).
