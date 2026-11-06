#!/usr/bin/env bash
# Puts a backup from deploy/backup.sh back: the database and the uploaded photos. Everything
# changed since that backup is lost. The site is down while it runs.
#
#   deploy/restore.sh /var/backups/xenonfix/2026-11-06_033000
set -euo pipefail

backup=${1:?Which backup? For example: deploy/restore.sh /var/backups/xenonfix/2026-11-06_033000}
backup=$(cd "$backup" && pwd)
cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f compose.prod.yaml ${COMPOSE_PROJECT:+-p "$COMPOSE_PROJECT"} ${ENV_FILE:+--env-file "$ENV_FILE"})

for file in database.sql.gz uploads.tar.gz; do
  [ -s "$backup/$file" ] || { echo "Missing $backup/$file" >&2; exit 1; }
done

if [ "${2:-}" != "--yes" ]; then
  read -r -p "Replace the site's database and photos with $backup? Type yes: " answer
  [ "$answer" = "yes" ] || { echo "Nothing changed."; exit 1; }
fi

"${COMPOSE[@]}" stop frontend backend

# The backup creates the database again; dropping it first also removes tables made after it
"${COMPOSE[@]}" exec -T mariadb sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -uroot -e "DROP DATABASE IF EXISTS xenonfix"'
gunzip -c "$backup/database.sql.gz" \
  | "${COMPOSE[@]}" exec -T mariadb sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -uroot'

"${COMPOSE[@]}" run --rm --no-deps -T --entrypoint sh backend \
  -c 'find /data/uploads -mindepth 1 -delete && tar -C /data/uploads -xzf -' < "$backup/uploads.tar.gz"

"${COMPOSE[@]}" start backend frontend
echo "Restored $backup"
