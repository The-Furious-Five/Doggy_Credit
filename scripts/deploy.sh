#!/usr/bin/env bash
# Ejecutar en el VPS dentro de la release extraída (lo invoca el job deploy de
# ci-cd.yml). No compila ni crea secretos: descarga las imágenes de GHCR,
# arranca y valida salud; si algo falla, vuelve a la release anterior.
set -Eeuo pipefail
base=${DC_DEPLOY_BASE:-/srv/doggycredit}
release=$(pwd)
test -f "$base/.env"
bash scripts/ensure_deploy_env.sh "$base/.env"
compose() {
  docker compose --env-file "$base/.env" --env-file infra/release.env \
    -f infra/compose.yaml -f infra/compose.prod.yaml "$@"
}
compose config --quiet
compose pull --quiet
previous=$(readlink "$base/current" || true)
rollback() {
  trap - ERR
  if [ -n "$previous" ] && [ "$previous" != "$release" ]; then
    echo "Despliegue fallido: restaurando $previous" >&2
    cd "$previous" && compose up -d --no-build --remove-orphans --wait --wait-timeout 300
  fi
}
trap rollback ERR
compose up -d --no-build --remove-orphans --wait --wait-timeout 300 || {
  # La API aún no ha arrancado: conservar el diagnóstico de los simuladores
  # antes de que el rollback reemplace sus contenedores.
  compose logs --no-color --tail=60 wiremock-bank wiremock-wallet >&2 || true
  false
}
# La salud de la API no demuestra que se hayan montado los datos de banco/billetera.
# Si alguno falla, el trap restaura la release anterior antes de cambiar current.
bash scripts/check_simulators.sh --env-file "$base/.env" --env-file infra/release.env \
  -f infra/compose.yaml -f infra/compose.prod.yaml
# --import-realm no actualiza un realm existente: aplicar el tema de acceso en cada despliegue.
compose exec -T keycloak bash -c \
  '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master \
     --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" &&
   /opt/keycloak/bin/kcadm.sh update realms/doggycredit -s loginTheme=doggycredit \
     -s "displayNameHtml=Doggy<span class=\"dc-wordmark-accent\">Credit</span>"'
compose exec -T keycloak bash -s < scripts/sync_mobile_client.sh
origin=$(sed -n 's|^DC_KEYCLOAK_ISSUER_URI=\(.*\)/auth/realms/doggycredit$|\1|p' infra/release.env)
curl --fail --silent --show-error --retry 30 --retry-all-errors --retry-delay 2 "$origin/actuator/health" > /dev/null
ln -sfn "$release" "$base/current"
trap - ERR

# Limpieza: conservar siempre esta release y la anterior para rollback, además
# de completar hasta 3 con las releases más recientes. No confiar solo en mtime:
# al reintentar un SHA existente, los timestamps extraídos pueden ser antiguos.
protected=("$release")
if [ -n "$previous" ] && [ "$previous" != "$release" ]; then
  protected+=("$previous")
fi
keep_extra=$((3 - ${#protected[@]}))
while IFS= read -r old; do
  [ -n "$old" ] || continue
  skip=false
  for active in "${protected[@]}"; do
    if [ "$old" = "$active" ]; then
      skip=true
      break
    fi
  done
  if [ "$skip" = true ]; then
    continue
  fi
  if [ "$keep_extra" -gt 0 ]; then
    keep_extra=$((keep_extra - 1))
    continue
  fi
  # Los archivos de temas (y otros) pueden llegar como solo lectura desde el tar.
  # Habilitar permisos u+rwx permite borrarlos sin fallar ante archivos protegidos.
  # La release nueva ya está activa (trap - ERR): si una vieja no se puede borrar,
  # se avisa y se reintenta en el próximo despliegue, sin dejar el job en rojo
  # (LOS-109 / LOS-117). Pasa cuando infra/keycloak/themes de una release vieja
  # es de otro dueño (p. ej. creada por Docker como root al montar el volumen):
  # ni chmod ni rm pueden con ella sin sudo.
  chmod -R u+rwx "$old" 2>/dev/null || true
  if ! rm -rf -- "$old" 2>/dev/null; then
    echo "Aviso: no se pudo borrar la release vieja $old (¿archivos de root?). Bórrala una vez con: sudo rm -rf $old" >&2
  fi
done < <(find "$base/releases" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' \
  | sort -rn | cut -d' ' -f2-)
keep=$(cat infra/release.env "$previous/infra/release.env" 2>/dev/null || true)
# Con pipefail, un grep sin coincidencias sería el código de salida del despliegue.
{ docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '/doggycredit-(api|web):' || true; } | while read -r image; do
  grep -qF "$image" <<< "$keep" || docker rmi "$image" > /dev/null || true
done
