#!/usr/bin/env bash
set -uo pipefail
# Solo lectura. No instala, no aplica firewall/sysctl, no reinicia ni consume mensajes.
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
printf '\n--- RAM ---\n'; free -h
printf '\n--- Disco ---\n'; df -h
printf '\n--- Carga ---\n'; uptime
printf '\n--- Sockets (sin sudo automatico) ---\n'; ss -tulpn
if command -v docker >/dev/null; then
  printf '\n--- Docker ---\n'; docker stats --no-stream
  docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
  if [[ -n ${1:-} && -f $1 ]]; then
    docker compose --env-file "$1" -f "$ROOT/compose.infra.yml" -f "$ROOT/compose.services.yml" \
      -f "$ROOT/compose.integration.yml" -f "$ROOT/compose.ec2.yml" ps
  fi
fi
printf '\nRevision manual adicional: journalctl, auditd, aa-status, nft list ruleset, Lynis.\n'
printf 'Revisar/redactar datos sensibles antes de compartir capturas o logs.\n'
