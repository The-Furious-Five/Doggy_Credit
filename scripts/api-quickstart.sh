#!/usr/bin/env bash
# D-11 (RF-API-01): los pasos 2 a 6 de docs/guia-inicio-rapido-api.md, en orden.
#
#   DC_URL=https://localhost DC_CLIENT_ID=... DC_CLIENT_SECRET=... DC_TOKEN_URL=... \
#     bash scripts/api-quickstart.sh [documento]
#
# Token -> solicitante -> consentimiento -> evaluación con Idempotency-Key -> lectura,
# y un reintento con la misma clave para mostrar que devuelve la misma evaluación.
# Solo datos sintéticos (por defecto CI-DEMO-0001005, perfil «buen pagador»).
set -euo pipefail

: "${DC_URL:?falta DC_URL, por ejemplo https://localhost}"
: "${DC_CLIENT_ID:?falta DC_CLIENT_ID (Desarrolladores › Crear clave)}"
: "${DC_CLIENT_SECRET:?falta DC_CLIENT_SECRET}"
: "${DC_TOKEN_URL:?falta DC_TOKEN_URL}"
DOCUMENTO="${1:-CI-DEMO-0001005}"
# En Compose local el certificado lo firma la CA de Caddy, que el sistema no conoce.
CURL=(curl -sS)
case "$DC_URL" in https://localhost*) CURL+=(-k) ;; esac
JSON='Content-Type: application/json'

step() { printf '\n== %s\n' "$1"; }

step "2. Token (client_credentials)"
token_response="$("${CURL[@]}" -X POST "$DC_TOKEN_URL" -d grant_type=client_credentials \
  -d client_id="$DC_CLIENT_ID" --data-urlencode client_secret="$DC_CLIENT_SECRET")"
DC_TOKEN="$(jq -r '.access_token // empty' <<<"$token_response")"
[ -n "$DC_TOKEN" ] || { echo "No se obtuvo token: $(jq -c '{error, error_description}' <<<"$token_response")" >&2; exit 1; }
jq '{token_type, expires_in}' <<<"$token_response"
AUTH="Authorization: Bearer $DC_TOKEN"

step "3. Solicitante $DOCUMENTO"
applicant="$("${CURL[@]}" -w '\n%{http_code}' -X POST "$DC_URL/api/v1/solicitantes" -H "$AUTH" -H "$JSON" \
  -d "$(jq -n --arg d "$DOCUMENTO" '{documento: $d, actividad: "Venta de abarrotes",
        antiguedadActividadMeses: 36, ingresosMensuales: "4200.00", moneda: "BOB",
        destinoCredito: "Capital de trabajo para mercadería"}')")"
status="$(tail -n1 <<<"$applicant")"
echo "HTTP $status $(sed '$d' <<<"$applicant" | jq -c '{id, code}')"
case "$status" in 201|409) ;; *) exit 1 ;; esac

step "4. Consentimiento"
consent="$("${CURL[@]}" -X POST "$DC_URL/api/v1/consentimientos" -H "$AUTH" -H "$JSON" \
  -d "$(jq -n --arg d "$DOCUMENTO" '{titularDocumento: $d, fuentes: ["BURO", "BANCO", "WALLET"],
        finalidad: "Evaluación de un microcrédito para capital de trabajo", vigenciaDias: 90}')")"
DC_CONSENT="$(jq -r '.id // empty' <<<"$consent")"
[ -n "$DC_CONSENT" ] || { echo "Consentimiento rechazado: $(jq -c . <<<"$consent")" >&2; exit 1; }
jq -c '{id, estado, fuentes, expiraEn}' <<<"$consent"

step "5. Evaluación (Idempotency-Key)"
DC_IDEMPOTENCY="$(uuidgen)"
body="$(jq -n --arg d "$DOCUMENTO" --arg c "$DC_CONSENT" '{titularDocumento: $d, consentId: $c,
        proposito: "Capital de trabajo", montoSolicitado: "15000", plazoMeses: 12}')"
assessment="$("${CURL[@]}" -X POST "$DC_URL/api/v1/credit-assessments" -H "$AUTH" -H "$JSON" \
  -H "Idempotency-Key: $DC_IDEMPOTENCY" -d "$body")"
DC_ASSESSMENT="$(jq -r '.id // empty' <<<"$assessment")"
[ -n "$DC_ASSESSMENT" ] || { echo "Evaluación rechazada: $(jq -c . <<<"$assessment")" >&2; exit 1; }
jq '{id, estado, decision, score, banda, razones, simulado, correlationId,
     elegibilidad: [.elegibilidad[]? | {tipoProducto, elegible}],
     recomendaciones: (if .recomendaciones then [.recomendaciones[] | {tipoProducto, montoSugerido}]
                       else "solo en el plan Pro" end)}' <<<"$assessment"

retry="$("${CURL[@]}" -X POST "$DC_URL/api/v1/credit-assessments" -H "$AUTH" -H "$JSON" \
  -H "Idempotency-Key: $DC_IDEMPOTENCY" -d "$body" | jq -r .id)"
echo "Reintento con la misma Idempotency-Key: $( [ "$retry" = "$DC_ASSESSMENT" ] && echo "misma evaluación ($retry)" || echo "OTRA evaluación ($retry)")"

step "6. Lectura del resultado"
"${CURL[@]}" "$DC_URL/api/v1/credit-assessments/$DC_ASSESSMENT" -H "$AUTH" \
  | jq -c '{id, estado, decision, score, banda}'
