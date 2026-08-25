#!/usr/bin/env bash
#
# db_backup.sh — take a safe, restorable snapshot of the Postgres database
# BEFORE applying new Flyway migrations or deploying a new phase.
#
# Secrets come ONLY from the environment (never hardcoded):
#   - DATABASE_URL                (preferred; e.g. Render's postgres://user:pass@host:port/db)
#   OR the individual Spring vars:
#   - SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
#
# Usage:
#   export DATABASE_URL='postgresql://user:pass@host:5432/smart_job_tracker'
#   ./scripts/db_backup.sh                 # writes backups/sjt_<utc-timestamp>.dump
#   ./scripts/db_backup.sh V5_profile      # adds a label:  backups/sjt_<ts>_V5_profile.dump
#
# Output: a pg_dump custom-format (-Fc) archive — compressed and selectively
# restorable with pg_restore. The archive is verified (pg_restore --list) after write.
#
set -euo pipefail

LABEL="${1:-}"
OUT_DIR="${BACKUP_DIR:-backups}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$OUT_DIR"

# Resolve a libpq-compatible connection string from the environment.
CONN="${DATABASE_URL:-${SPRING_DATASOURCE_URL:-}}"
if [[ -z "$CONN" ]]; then
  echo "ERROR: set DATABASE_URL (or SPRING_DATASOURCE_URL) in the environment." >&2
  exit 1
fi
# Spring uses jdbc:postgresql://... ; strip the jdbc: prefix for pg_dump/libpq.
CONN="${CONN#jdbc:}"

# If username/password are provided separately (Spring style), export them for libpq.
if [[ -n "${SPRING_DATASOURCE_USERNAME:-}" ]]; then export PGUSER="$SPRING_DATASOURCE_USERNAME"; fi
if [[ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then export PGPASSWORD="$SPRING_DATASOURCE_PASSWORD"; fi

SUFFIX=""
[[ -n "$LABEL" ]] && SUFFIX="_${LABEL}"
OUT_FILE="${OUT_DIR}/sjt_${TS}${SUFFIX}.dump"

echo "==> Backing up to ${OUT_FILE}"
pg_dump --format=custom --no-owner --no-privileges --file="$OUT_FILE" "$CONN"

echo "==> Verifying archive integrity"
pg_restore --list "$OUT_FILE" > /dev/null
BYTES="$(wc -c < "$OUT_FILE")"
echo "==> OK. ${OUT_FILE} (${BYTES} bytes)"
echo "    Restore with:  ./scripts/db_restore.sh ${OUT_FILE}"
