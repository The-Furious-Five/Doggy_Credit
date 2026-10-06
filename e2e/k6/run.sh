#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../.." && pwd)

if ! command -v k6 >/dev/null 2>&1; then
  echo 'Falta k6. Instala la versión 1.8.1 indicada en el runbook E-07.' >&2
  exit 127
fi
installed_version=$(k6 version)
if [[ "$installed_version" != 'k6 v1.8.1 '* && "$installed_version" != 'k6 v1.8.1' ]]; then
  echo 'Esta prueba usa k6 1.8.1 y su formato clásico de resumen; instala esa versión.' >&2
  exit 2
fi
task_python=${K6_PYTHON:-python3}
"$task_python" "$script_dir/generate_fixture.py"
mkdir -p "$script_dir/results"
export K6_COMMIT
K6_COMMIT=$(git -C "$repo_root" rev-parse HEAD)
# El reporte usa el formato clásico de k6 1.x, independiente del entorno del usuario.
export K6_NEW_MACHINE_READABLE_SUMMARY=false
cd "$script_dir"
exec k6 run credit-assessments.js
