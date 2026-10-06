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
  # Las carpetas empaquetadas pueden ser de solo lectura (p. ej., temas).
  # Habilitar escritura en directorios permite borrarlas sin elevar privilegios.
  # La release nueva ya está activa (trap - ERR): si una vieja no se puede borrar
  # (archivos de otro dueño, como los temas que escribe Keycloak), se avisa y se
  # reintenta en el próximo despliegue, sin dejar el despliegue en rojo (LOS-109).
  find "$old" -type d -exec chmod u+rwx {} + 2>/dev/null || true
  if ! rm -rf -- "$old" 2>/dev/null; then
    echo "Aviso: no se pudo borrar la release vieja $old; se reintentará en el próximo despliegue." >&2
  fi
done < <(find "$base/releases" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' \
  | sort -rn | cut -d' ' -f2-)
keep=$(cat infra/release.env "$previous/infra/release.env" 2>/dev/null || true)
docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '/doggycredit-(api|web):' | while read -r image; do
  grep -qF "$image" <<< "$keep" || docker rmi "$image" > /dev/null || true
done
