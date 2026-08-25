#!/usr/bin/env bash
#
# db_restore.sh — restore a pg_dump (-Fc) archive created by db_backup.sh.
#
# Because Flyway migrations in this project are ADDITIVE-ONLY (no destructive
# DROP/ALTER), a restore is rarely needed — but keep the ability for peace of mind
# before each phase. Restores are destructive to the TARGET database, so this script
# requires an explicit confirmation.
#
# Secrets come ONLY from the environment (never hardcoded):
#   - DATABASE_URL  (or SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD)
#
# Usage:
#   export DATABASE_URL='postgresql://user:pass@host:5432/smart_job_tracker'
#   ./scripts/db_restore.sh backups/sjt_20260826T101500Z.dump
#
set -euo pipefail

ARCHIVE="${1:-}"
if [[ -z "$ARCHIVE" || ! -f "$ARCHIVE" ]]; then
  echo "ERROR: pass the path to a .dump archive.  e.g. ./scripts/db_restore.sh backups/sjt_<ts>.dump" >&2
  exit 1
fi

CONN="${DATABASE_URL:-${SPRING_DATASOURCE_URL:-}}"
if [[ -z "$CONN" ]]; then
  echo "ERROR: set DATABASE_URL (or SPRING_DATASOURCE_URL) in the environment." >&2
  exit 1
fi
CONN="${CONN#jdbc:}"
if [[ -n "${SPRING_DATASOURCE_USERNAME:-}" ]]; then export PGUSER="$SPRING_DATASOURCE_USERNAME"; fi
if [[ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then export PGPASSWORD="$SPRING_DATASOURCE_PASSWORD"; fi

echo "!! This will restore '${ARCHIVE}' into the target database and OVERWRITE existing objects."
read -r -p "Type 'RESTORE' to proceed: " CONFIRM
[[ "$CONFIRM" == "RESTORE" ]] || { echo "Aborted."; exit 1; }

echo "==> Restoring ${ARCHIVE}"
# --clean --if-exists drops existing objects first; --no-owner/--no-privileges keeps it portable across roles.
pg_restore --clean --if-exists --no-owner --no-privileges --dbname="$CONN" "$ARCHIVE"
echo "==> Restore complete."
