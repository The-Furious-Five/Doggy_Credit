#!/usr/bin/env bash
# Una release anterior puede no tener la contraseña de la nueva cuenta de plataforma.
# Generarla una sola vez evita intervención manual y reutilización de otras claves.
set -euo pipefail
env_file=${1:?falta la ruta del .env de producción}
test -f "$env_file"
present=false
while IFS= read -r line || [ -n "$line" ]; do
  if [[ "$line" =~ ^[[:space:]]*(export[[:space:]]+)?DC_SEED_PLATFORM_ADMIN_PASSWORD[[:space:]]*=[[:space:]]*(.*)$ ]]; then
    value=${BASH_REMATCH[2]}
    value=${value#"${value%%[![:space:]]*}"}
    case "$value" in
      ''|\"\"|\'\'|\#*) present=false ;;
      *) present=true ;;
    esac
  fi
done < "$env_file"
if [ "$present" = false ]; then
  password=$(od -An -N32 -tx1 /dev/urandom | tr -d '[:space:]')
  [[ "$password" =~ ^[0-9a-f]{64}$ ]]
  chmod 600 "$env_file"
  printf '\nDC_SEED_PLATFORM_ADMIN_PASSWORD=%s\n' "$password" >> "$env_file"
  echo "Contraseña inicial de plataforma generada y guardada en el .env de producción"
fi
