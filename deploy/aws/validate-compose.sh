#!/usr/bin/env bash
set -euo pipefail
# Solo config: nunca up/down/build; el JSON con secretos fluye por pipe, no a disco/stdout.
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
ENV_FILE=${1:?Uso: bash deploy/aws/validate-compose.sh /ruta/externa/.env.aws}
command -v docker >/dev/null || { echo 'Docker no disponible; no se valido Compose.' >&2; exit 1; }
command -v python3 >/dev/null || { echo 'Se requiere python3.' >&2; exit 1; }
test -f "$ENV_FILE" || { echo 'No existe el archivo de entorno indicado.' >&2; exit 1; }
VERSION=$(docker compose version --short)
python3 - "$VERSION" <<'PY'
import re, sys
v = re.match(r'^v?(\d+)\.(\d+)\.(\d+)', sys.argv[1])
if not v or tuple(map(int, v.groups())) < (2, 24, 4):
    sys.exit('Se requiere Compose >= 2.24.4 para !override. No quitar etiquetas para continuar.')
PY
dc=(docker compose --env-file "$ENV_FILE" -f "$ROOT/compose.infra.yml" -f "$ROOT/compose.services.yml"
    -f "$ROOT/compose.integration.yml" -f "$ROOT/compose.ec2.yml")
"${dc[@]}" config --quiet
"${dc[@]}" config --services
"${dc[@]}" config --format json | python3 "$ROOT/deploy/aws/check-effective-compose.py"
