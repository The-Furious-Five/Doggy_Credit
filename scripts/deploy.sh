#!/usr/bin/env bash
# Ejecutar en el VPS dentro de la release extraída (lo invoca el job deploy de
# ci-cd.yml). No compila ni crea secretos: descarga las imágenes de GHCR,
# arranca y valida salud; si algo falla, vuelve a la release anterior.
set -euo pipefail
base=/srv/doggycredit
release=$(pwd)
test -f "$base/.env"
compose() {
  docker compose --env-file "$base/.env" --env-file infra/release.env \
    -f infra/compose.yaml -f infra/compose.prod.yaml "$@"
}
compose pull --quiet
previous=$(readlink "$base/current" || true)
rollback() {
  if [ -n "$previous" ] && [ "$previous" != "$release" ]; then
    echo "Despliegue fallido: restaurando $previous" >&2
    cd "$previous" && compose up -d --remove-orphans
  fi
}
trap rollback ERR
compose up -d --remove-orphans --wait --wait-timeout 300
# --import-realm no actualiza un realm existente: aplicar el tema de acceso en cada despliegue.
compose exec -T keycloak bash -c \
  '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master \
     --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" &&
   /opt/keycloak/bin/kcadm.sh update realms/doggycredit -s loginTheme=doggycredit \
     -s "displayNameHtml=Doggy<span class=\"dc-wordmark-accent\">Credit</span>"'
origin=$(sed -n 's|^DC_KEYCLOAK_ISSUER_URI=\(.*\)/auth/realms/doggycredit$|\1|p' infra/release.env)
curl --fail --silent --show-error --retry 30 --retry-all-errors --retry-delay 2 "$origin/actuator/health" > /dev/null
ln -sfn "$release" "$base/current"
trap - ERR

# Limpieza: se conservan las 3 releases más recientes y solo las imágenes de
# la release actual y la anterior (las necesarias para volver atrás).
ls -1dt "$base"/releases/*/ | tail -n +4 | xargs -r rm -rf
keep=$(cat infra/release.env "$previous/infra/release.env" 2>/dev/null || true)
docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '/doggycredit-(api|web):' | while read -r image; do
  grep -qF "$image" <<< "$keep" || docker rmi "$image" > /dev/null || true
done
