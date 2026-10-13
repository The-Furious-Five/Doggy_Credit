#!/usr/bin/env bash
# Se ejecuta dentro del contenedor Keycloak. No requiere Python ni jq en el VPS.
set -euo pipefail
umask 077
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
mobile_file=${DC_MOBILE_CLIENT_FILE:-/opt/keycloak/doggycredit-mobile.json}
test -s "$mobile_file"
kc() {
  "${DC_KCADM:-/opt/keycloak/bin/kcadm.sh}" "$@" --config "$temporary/kcadm.config"
}
kc config credentials --server http://localhost:8080/auth --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"

# Keycloak ignora scopes inexistentes: fallar antes de crear un cliente incompleto.
scopes=$(kc get client-scopes -r doggycredit --fields name --format csv --noquotes)
for scope in basic profile email roles; do
  case $'\n'"$scopes"$'\n' in
    *$'\n'"$scope"$'\n'*) ;;
    *) echo "Falta el scope $scope del cliente móvil" >&2; exit 1 ;;
  esac
done
client_uuid=$(kc get clients -r doggycredit -q clientId=doggycredit-mobile \
  --fields id --format csv --noquotes)
if [ -z "$client_uuid" ]; then
  client_uuid=$(kc create clients -r doggycredit -f "$mobile_file" -i)
else
  # Una respuesta múltiple o inesperada no debe convertirse en una actualización.
  [[ "$client_uuid" =~ ^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$ ]]
  kc update "clients/$client_uuid" -r doggycredit -f "$mobile_file"
fi
echo "Cliente doggycredit-mobile sincronizado (PKCE S256, tenant y audiencia API)"
