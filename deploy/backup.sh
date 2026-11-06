#!/usr/bin/env bash
# Backs up the database and the uploaded photos together, into one dated folder, and keeps the
# newest $KEEP. Run on the server from cron, see README. Copy the folder off the server too: a
# backup on the same disk is lost with it.
#
#   deploy/backup.sh                 into /var/backups/xenonfix
#   BACKUP_DIR=/elsewhere deploy/backup.sh
set -euo pipefail

cd "$(dirname "$0")/.."
BACKUP_DIR=${BACKUP_DIR:-/var/backups/xenonfix}
KEEP=${KEEP:-14}
COMPOSE=(docker compose -f compose.prod.yaml ${COMPOSE_PROJECT:+-p "$COMPOSE_PROJECT"} ${ENV_FILE:+--env-file "$ENV_FILE"})

stamp=$(date -u +%Y-%m-%d_%H%M%S)
partial="$BACKUP_DIR/.$stamp.partial"
mkdir -p "$partial"
trap 'rm -rf "$partial"' EXIT

# The password is read inside the container from its own settings, so it never appears in a
# command line, this script's output or a log
"${COMPOSE[@]}" exec -T mariadb sh -c \
  'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb-dump -uroot --single-transaction --routines --triggers --databases xenonfix' \
  | gzip > "$partial/database.sql.gz"
"${COMPOSE[@]}" exec -T backend tar -C /data/uploads -czf - . > "$partial/uploads.tar.gz"

# Only a complete backup gets its real name
mv "$partial" "$BACKUP_DIR/$stamp"
trap - EXIT

# Oldest first; the names sort by date
ls -1d "$BACKUP_DIR"/20*/ | head -n -"$KEEP" | xargs -r rm -rf
echo "Backup written to $BACKUP_DIR/$stamp"
