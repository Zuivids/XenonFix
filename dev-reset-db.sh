#!/usr/bin/env bash
# Deletes the LOCAL database with all its data. The next ./dev-start.sh creates a fresh one.
# Uploaded files and .env stay.
set -euo pipefail
cd "$(dirname "$0")"

read -r -p "Delete ALL local XenonFix data? Type YES to continue: " answer
[ "$answer" = "YES" ] || { echo "Cancelled."; exit 0; }

bash ./dev-stop.sh
if [ -f .env ]; then
  docker compose down -v
fi
echo "Local database deleted. Run ./dev-start.sh to start with a fresh one."
