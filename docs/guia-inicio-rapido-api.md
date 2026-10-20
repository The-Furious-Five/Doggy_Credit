# Guía de inicio rápido de la API pública

Para quien integra DoggyCredit en su propio sistema: el core de una cooperativa, la
app de una fintech, un proceso por lotes. Al terminar habrás creado una evaluación de
crédito y leído su resultado con tu propia clave de API. Toma unos 15 minutos.

La API es `/api/v1`, REST con JSON, autenticada con OAuth 2.0 `client_credentials`. El
contrato completo está en
[`contracts/openapi/doggycredit-api.v1.yaml`](../contracts/openapi/doggycredit-api.v1.yaml)
(OpenAPI 3.1): ábrelo en cualquier visor de OpenAPI o genera un cliente desde él.

## Antes de empezar

- Una cuenta con rol **administrador** en un tenant de DoggyCredit, para crear la clave.
- `curl` y `jq` en la terminal.
- La URL base de tu entorno. En esta guía:

```bash
export DC_URL=https://localhost          # Compose local; en la demo: https://doggycredit.los5furiosos.cloud
```

> En Compose local el certificado es de Caddy y tu sistema no lo conoce: agrega `-k` a
> cada `curl` o instala la CA local de Caddy. En la demo no hace falta.

Todos los datos de los ejemplos son sintéticos. Nunca envíes datos de personas reales a
un entorno de demostración.

## 1. Crea tu clave de API

En la consola: **Desarrolladores › Crear clave**.

1. Ponle un nombre que la identifique, por ejemplo `Core cooperativa`.
2. Elige el entorno: **Sandbox** (datos de prueba) o **Producción** (requiere el plan Pro).
3. Pulsa **Crear clave**. El diálogo «Clave creada» muestra tres datos:

| Dato | Para qué |
|---|---|
| **Client ID** | Identifica tu clave al pedir el token |
| **Secreto** | Se muestra **una sola vez**: cópialo ahora y guárdalo en tu gestor de secretos |
| **Token** | La URL donde se piden los tokens |

Si pierdes el secreto, revoca la clave en la misma pantalla y crea otra. Nunca lo
pongas en el código ni en un repositorio.

```bash
export DC_CLIENT_ID='<Client ID>'
export DC_CLIENT_SECRET='<Secreto>'
export DC_TOKEN_URL='<Token>'
```

## 2. Pide un token

```bash
export DC_TOKEN=$(curl -s -X POST "$DC_TOKEN_URL" \
  -d grant_type=client_credentials \
  -d client_id="$DC_CLIENT_ID" \
  --data-urlencode client_secret="$DC_CLIENT_SECRET" | jq -r .access_token)
```

El token dura **5 minutos** (`expires_in: 300`). Pide uno nuevo cuando venza; no hay
refresh token en este flujo. El token ya lleva tu tenant: la API nunca lo toma de una
cabecera, de la ruta ni del cuerpo, así que no hay forma de pedir datos de otra
institución.

## 3. Registra al solicitante

```bash
curl -s -X POST "$DC_URL/api/v1/solicitantes" \
  -H "Authorization: Bearer $DC_TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "documento": "CI-DEMO-0001005",
    "actividad": "Venta de abarrotes",
    "antiguedadActividadMeses": 36,
    "ingresosMensuales": "4200.00",
    "moneda": "BOB",
    "destinoCredito": "Capital de trabajo para mercadería"
  }' | jq
```

Responde `201` con el solicitante y su `id`. Si el documento ya estaba registrado en tu
tenant responde `409 APPLICANT_ALREADY_EXISTS`: no es un problema, sigue al paso 4.

Los montos van siempre como **texto decimal** (`"4200.00"`), nunca como número de
coma flotante.

## 4. Registra el consentimiento

DoggyCredit no consulta ninguna fuente sin el consentimiento del titular para esa fuente
y esa finalidad.

```bash
export DC_CONSENT=$(curl -s -X POST "$DC_URL/api/v1/consentimientos" \
  -H "Authorization: Bearer $DC_TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "titularDocumento": "CI-DEMO-0001005",
    "fuentes": ["BURO"],
    "finalidad": "Evaluación de un microcrédito para capital de trabajo",
    "vigenciaDias": 90
  }' | jq -r .id)
```

`fuentes` admite `BURO`, `BANCO` y `WALLET`:

- **El buró es obligatorio.** Sin consentimiento vigente para `BURO`, la evaluación responde
  `422 CONSENT_REQUIRED` y no consume cuota.
- **Banco y billetera son opcionales.** Si tu tenant los tiene conectados pero el
  consentimiento no los incluye, la evaluación sigue sin consultarlos. Con datos alternativos
  el score del plan Pro es más preciso, así que inclúyelos si el titular lo autoriza.

## 5. Crea la evaluación

```bash
export DC_IDEMPOTENCY=$(uuidgen)
export DC_ASSESSMENT=$(curl -s -X POST "$DC_URL/api/v1/credit-assessments" \
  -H "Authorization: Bearer $DC_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $DC_IDEMPOTENCY" \
  -d "{
    \"titularDocumento\": \"CI-DEMO-0001005\",
    \"consentId\": \"$DC_CONSENT\",
    \"proposito\": \"Capital de trabajo\",
    \"montoSolicitado\": \"15000\",
    \"plazoMeses\": 12
  }" | tee /dev/stderr | jq -r .id)
```

La `Idempotency-Key` es obligatoria y te protege de los reintentos:

- **La misma clave con el mismo cuerpo** devuelve la misma evaluación y no consume cuota
  otra vez. Si se te corta la conexión, reintenta con la misma clave.
- **La misma clave con otro cuerpo** responde `422 IDEMPOTENCY_KEY_REUSED`.
- Una evaluación nueva lleva una clave nueva.

Responde `201` con el resultado. Esta es una respuesta real del entorno local, en el plan
Gratis (resumida):

```json
{
  "id": "211478ba-7d3a-4e84-a806-f729598236c6",
  "estado": "PREAPROBADO",
  "decision": "PREAPROBADO",
  "score": 770,
  "banda": "A",
  "razones": ["MONTO_4_6_INGRESOS", "HISTORIAL_6_23_MESES", "SIN_MORA_12M",
              "ACTIVIDAD_MAYOR_24_MESES", "PLAZO_HASTA_12_MESES", "CON_REGISTRO_BURO"],
  "simulado": true,
  "correlationId": "3214ff0e-2265-4711-bed4-a62ce25d6e80",
  "elegibilidad": [ { "tipoProducto": "MICROCREDITO", "elegible": false, "criterios": [ … ] } ]
}
```

| Campo | Qué significa |
|---|---|
| `estado` | `PREAPROBADO`, `REVISION_MANUAL`, `PARCIAL` (faltó una fuente no obligatoria) o `FALLIDA` |
| `decision` | La de las políticas de tu tenant: `PREAPROBADO`, `REVISION_MANUAL` o `SOLICITAR_DATOS`. **Nunca hay rechazo automático**: un rechazo siempre lo decide una persona |
| `score`, `banda` | 0–1000 y A–E |
| `razones` | Al menos 3 códigos que explican el score, de mayor a menor impacto (p. ej. `SIN_MORA_12M`). Tradúcelos a tus propios mensajes |
| `elegibilidad` | Cada producto activo de tu catálogo (`tipoProducto`) con sus criterios y si cumple todos (`elegible`) |
| `recomendaciones` | Solo en Pro: los productos elegibles, del más adecuado al menos |
| `simulado` | `true` mientras las fuentes son simuladores |
| `correlationId` | Identificador de la operación: inclúyelo si reportas un problema |

## 6. Lee el resultado

```bash
curl -s "$DC_URL/api/v1/credit-assessments/$DC_ASSESSMENT" \
  -H "Authorization: Bearer $DC_TOKEN" | jq
```

Una evaluación de otro tenant responde `404`, igual que una que no existe: la API nunca
confirma que algo ajeno exista. Para listar las de un titular:
`GET /api/v1/credit-assessments?solicitanteId=<id>`.

## Errores

Todo error sigue RFC 9457 (`application/problem+json`) y trae un `code` estable y el
`correlationId`. Decide por `code`, no por el texto:

| HTTP | `code` | Qué hacer |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Revisa el cuerpo: un campo falta o no tiene el formato del contrato |
| 401 | `UNAUTHENTICATED` | Token ausente o vencido: pide uno nuevo |
| 403 | `TENANT_SUSPENDED` | Tu institución está suspendida: contacta a DoggyCredit |
| 403 | `FEATURE_NOT_IN_PLAN` | La función requiere el plan Pro |
| 409 | `APPLICANT_ALREADY_EXISTS` | El solicitante ya está registrado: continúa |
| 422 | `CONSENT_REQUIRED` | Falta un consentimiento vigente para el buró, o el `consentId` no es de ese titular |
| 422 | `APPLICANT_NOT_FOUND` | Registra al solicitante antes de evaluarlo |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Usa una clave nueva para una evaluación distinta |
| 429 | `RATE_LIMITED` | Superaste las solicitudes por minuto: espera lo que indica `Retry-After` |
| 429 | `QUOTA_EXCEEDED` | Se agotaron las evaluaciones del mes de tu plan |
| 502 | `PROVIDER_UNAVAILABLE` | Ninguna fuente respondió; no se cobró. Reintenta más tarde |

El catálogo completo está en [`contracts/errors/catalog.yaml`](../contracts/errors/catalog.yaml).

## Límites por plan

| Plan | Evaluaciones por mes | Solicitudes por minuto | Claves de producción |
|---|---|---|---|
| Gratis | 500 | 30 | No (solo sandbox) |
| Pro | Sin límite | 300 | Sí |
| Enterprise | Sin límite | 1000 | Sí |

Una evaluación consume cuota solo si se crea: los reintentos con la misma
`Idempotency-Key`, las `FALLIDA` y los errores de precondición no cuentan. Tu consumo del mes
está en `GET /api/v1/tenant/actual/uso` (`evaluacionesMes` y `limiteMensual`, 0 = sin límite).

## Todo junto

[`scripts/api-quickstart.sh`](../scripts/api-quickstart.sh) recorre los pasos 2 a 6 con
las variables de esta guía, y además reintenta la evaluación con la misma `Idempotency-Key`
para mostrar que devuelve la misma:

```bash
DC_URL=https://localhost DC_CLIENT_ID=... DC_CLIENT_SECRET=... DC_TOKEN_URL=... \
  bash scripts/api-quickstart.sh
```

## Verificación de esta guía

Se recorrió completa el 20/10/2026 contra el Compose local (`.env.example`, datos
sintéticos), con las claves M2M sembradas del tenant Gratis y del Pro, que tienen la misma
forma que una clave creada desde la consola: `tenant_id`, audiencia `doggycredit-api` y rol
`API_CLIENT`. Crear la clave desde la consola lo cubre `e2e/identity_smoke.py` en el CI.
Cada error de la tabla se provocó a propósito y respondió el `code` indicado.

