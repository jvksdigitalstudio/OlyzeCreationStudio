#!/usr/bin/env bash
# Analizadores de la auditoría (docs/AUDITORIA_COMPLETA_2026-10-29.md). Heurísticos: señalan candidatos que hay que VERIFICAR a mano.
set -uo pipefail
cd "$(dirname "$0")"
for s in imports dead_private dead_public catches clones bigfuncs mainthread_io vm_main_blocking; do
  echo; echo "=================== $s ==================="
  python3 "$s.py" || echo "(falló $s.py)"
done
