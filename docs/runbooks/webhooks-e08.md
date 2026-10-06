# Webhooks por tenant (E-08 / LOS-103)

La tarjeta Webhooks de `/app/desarrolladores` consulta y guarda la configuración
real. Se requiere el rol `TENANT_ADMIN` y un plan con Webhooks habilitados (PRO o
ENTERPRISE). El tenant viene del JWT y el plan se consulta en el registro del
servidor. FREE recibe `403 FEATURE_NOT_IN_PLAN` en GET y PUT.

## Configurar el receptor

La API pública expone `/api/v1/webhooks` mediante el proxy; el controlador del
backend atiende `/webhooks`. El contrato está en
`contracts/openapi/doggycredit-api.v1.yaml`. Los clientes Java y Angular se generan
desde ese archivo.

`GET /webhooks` devuelve una configuración vacía si aún no existe:

```json
{"url": null, "eventos": [], "secretoConfigurado": false, "actualizadaEn": null}
```

`PUT /webhooks` acepta una URL HTTPS pública, los eventos y un secreto de entre
32 y 128 caracteres. Utiliza un secreto aleatorio distinto para cada receptor;
compártelo con el receptor antes de activar las entregas.

```json
{
  "url": "https://receptor.example.com/doggycredit",
  "eventos": ["CreditAssessmentCompleted.v1", "ConsentRevoked.v1"],
  "secreto": "un-secreto-aleatorio-de-al-menos-32-caracteres"
}
```

Se admiten `ConsentGranted.v1`, `ConsentRevoked.v1` y
`CreditAssessmentCompleted.v1`, con los payloads D-03 de `contracts/events/`.
Omitir `secreto` conserva el actual; la primera configuración lo requiere.
El servidor nunca devuelve el secreto. Se cifra por tenant tanto en la
configuración como en la cola, junto con el payload pendiente.

Para desactivar, envía `{"url": null, "eventos": []}`. Cambiar la URL, el
conjunto de eventos o el secreto cancela las entregas pendientes anteriores.
Una entrega que ya comenzó puede finalizar. Guardar de nuevo la misma
configuración conserva su cola. La desactivación elimina el secreto de la
configuración; volver a activar requiere otro secreto.

## Verificar una entrega

Cada POST tiene `Content-Type: application/json` y estas cabeceras:

| Cabecera | Valor |
| --- | --- |
| `X-DoggyCredit-Event` | Nombre versionado, por ejemplo `CreditAssessmentCompleted.v1` |
| `X-DoggyCredit-Event-Id` | UUID del evento, estable en los reintentos |
| `X-DoggyCredit-Timestamp` | Segundos Unix del intento de entrega |
| `X-DoggyCredit-Signature` | `sha256=` seguido del HMAC-SHA256 hexadecimal |

El mensaje firmado es `timestamp + "." + cuerpo JSON original`, codificado en
UTF-8. Verifica los bytes recibidos antes de parsear o reformatear JSON. Ejemplo
de verificación en el receptor, usando la biblioteca estándar de Python:

```python
import hashlib
import hmac
import time


def firma_valida(secret: str, timestamp: str, signature: str, raw_body: bytes) -> bool:
    if not timestamp.isascii() or not timestamp.isdecimal():
        return False
    if abs(time.time() - int(timestamp)) > 300:
        return False
    message = timestamp.encode("ascii") + b"." + raw_body
    expected = "sha256=" + hmac.new(secret.encode("utf-8"), message, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature)
```

Después de verificar la firma, comprueba que `eventId` del cuerpo coincide con la
cabecera y guarda ese identificador al aceptar el evento. Responde con un 2xx
cuando puedas procesarlo de forma durable. Una entrega puede repetirse si el
receptor aceptó el evento pero la respuesta o la transacción local falló; el
receptor debe deduplicar por `eventId`. No se garantiza orden entre eventos.

## Cola, recuperación y límites

Los listeners transaccionales consumen el outbox D-03 y confirman una inserción
idempotente en `webhooks.deliveries`. El outbox se completa después de confirmar
esa inserción, sin esperar al receptor. El registro de publicaciones y la
recuperación usan las capacidades de
[Spring Modulith](https://docs.spring.io/spring-modulith/reference/events.html).
El worker recupera publicaciones Webhooks incompletas y procesa la cola cada
cinco segundos, con hasta veinte entregas por tenant en cada vuelta.

La cola persiste en PostgreSQL y aplica RLS al rol restringido de la aplicación.
`FOR UPDATE SKIP LOCKED` impide que dos workers envíen simultáneamente la misma
fila. Un 2xx marca `DELIVERED`; cualquier otro estado, un timeout o un error de
red programa un reintento. Hay cinco intentos totales y esperas mínimas de
30, 60, 120 y 240 segundos. El último fallo marca `FAILED`. Bajar a FREE cancela
las entregas pendientes cuando se procesa ese tenant.

El cliente conecta en hasta dos segundos, limita cada petición a tres segundos
y no sigue redirecciones. Valida la URL al guardar y antes de cada envío: HTTPS,
sin credenciales ni fragmentos, y sin direcciones locales o privadas. La
validación DNS no sustituye las restricciones de salida de la red del servidor;
configura el firewall de salida para proteger las redes internas.

Propiedades:

| Propiedad | Predeterminado | Uso |
| --- | --- | --- |
| `doggycredit.webhooks.worker-enabled` | `true` | Entrega periódica y recuperación del outbox |
| `doggycredit.webhooks.poll-delay` | `5000` | Espera entre vueltas, en milisegundos |
| `doggycredit.webhooks.allow-private-addresses` | `false` | Habilita HTTP y red privada exclusivamente para pruebas con WireMock |

Mantén `allow-private-addresses=false` en producción. Los logs no incluyen URL,
secreto ni payload; las filas guardan solo el último estado HTTP del receptor.

## Verificaciones antes de integrar

Desde la raíz del repositorio, con Java 21, Node 22 y Docker disponibles:

```bash
bash scripts/generate_clients.sh
git diff --exit-code -- web/src/app/api
npm run ds:check --prefix web
npm run ds:test --prefix web
CI=1 npm run build --prefix web
npm test --prefix web -- --watch=false
cd backend
./mvnw -B verify
```

`WebhookDeliveryTests` levanta PostgreSQL 17 y WireMock 3.9.1. Verifica la entrega
real de `CreditAssessmentCompleted.v1` desde el outbox y su firma, el bloqueo
FREE, roles, cifrado, aislamiento RLS, deduplicación, backoff, cancelación y el
límite de reintentos. La prueba falla si falta Docker: no se omite ni acepta un
404 como restricción de plan. El job Backend existente de CI la ejecuta con
`verify` y exige Docker.

La integración de esta rama requiere que ese job termine correctamente. Si
también se integran E-06 y E-07, corre sus jobs de Compose/Playwright y carga
contra la combinación de ramas; E-08 implementa el GET que E-06 exige para PRO.
