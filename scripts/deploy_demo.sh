#!/usr/bin/env bash
# Ejecutar en la VM dentro de la release extraída. No crea ni reemplaza secretos.
set -euo pipefail
release=$(pwd)
test -f /srv/doggycredit/.env
set -a
source /srv/doggycredit/.env
set +a
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements.txt
.venv/bin/python scripts/configure_demo.py
previous=$(readlink /srv/doggycredit/current || true)
# Etiquetas locales inmutables por revisión: permiten volver a la imagen previa.
revision=$(basename "$release")
[[ "$revision" =~ ^[0-9a-f]{40}$ ]]
cat > infra/release-images.yaml <<YAML
services:
  doggycredit-api:
    image: doggycredit-api:$revision
  caddy:
    image: doggycredit-web:$revision
YAML
docker compose --env-file /srv/doggycredit/.env -f infra/compose.yaml -f infra/release-images.yaml build
rollback() {
  if [ -n "$previous" ]; then
    cd "$previous"
    docker compose --env-file /srv/doggycredit/.env -f infra/compose.yaml -f infra/release-images.yaml up -d --no-build
  fi
}
trap rollback ERR
docker compose --env-file /srv/doggycredit/.env -f infra/compose.yaml -f infra/release-images.yaml up -d --wait --wait-timeout 180
origin="${DC_KEYCLOAK_ISSUER_URI%/auth/realms/doggycredit}"
for attempt in $(seq 1 30); do
  if curl --fail --silent "$origin/actuator/health" > /dev/null; then
    ln -sfn "$release" /srv/doggycredit/current
    trap - ERR
    exit 0
  fi
  sleep 2
done
false
