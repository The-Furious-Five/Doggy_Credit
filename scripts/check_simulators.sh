#!/usr/bin/env bash
# E-04: comprobar los simuladores por la red interna, sin publicar sus puertos.
# Acepta las mismas opciones globales que docker compose (--env-file, -f, etc.).
set -euo pipefail

check() {
  local service=$1 path=$2
  shift 2
  docker compose "$@" exec -T "$service" sh -ec '
    wget -q -T 5 -t 1 -O /dev/null http://127.0.0.1:8080/__admin/health
    body=$(wget -q -T 5 -t 1 -O - "http://127.0.0.1:8080/$1/CI-DEMO-0001001")
    printf "%s\n" "$body" | grep -qF CI-DEMO-0001001
  ' sh "$path"
  echo "OK: $service saludable y con datos sintéticos cargados"
}

check wiremock-bank bank/v1/clientes "$@"
check wiremock-wallet wallet/v1/titulares "$@"
