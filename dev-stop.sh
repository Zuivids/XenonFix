#!/usr/bin/env bash
# Stops the local system. The database keeps its data (reset it with ./dev-reset-db.sh).
set -uo pipefail
cd "$(dirname "$0")"

fail() { echo "ERROR: $*" >&2; exit 1; }

# Stops whatever listens on a port: the backend JVM (8090) and the Angular server (4300)
stop_port() {
  local port=$1 name=$2 pids=""
  if command -v lsof >/dev/null; then
    pids=$(lsof -ti "tcp:$port" -sTCP:LISTEN || true)
    [ -n "$pids" ] && kill $pids
  elif command -v netstat >/dev/null; then
    # Windows (Git Bash): netstat shows Windows process ids
    pids=$(netstat -ano | awk -v p=":$port" '$2 ~ p"$" && $4 == "LISTENING" {print $5}' | sort -u)
    for pid in $pids; do taskkill //F //T //PID "$pid" >/dev/null 2>&1; done
  fi
  if [ -n "$pids" ]; then echo "Stopped $name."; else echo "$name was not running."; fi
}

stop_port 4300 "frontend"
stop_port 8090 "backend"

if [ ! -f .env ]; then
  echo "No .env, so the database was never started."
  exit 0
fi

docker info >/dev/null 2>&1 || fail "Docker is not running, so the database could not be stopped."

echo "Stopping database..."
docker compose stop || fail "Could not stop the database. See the lines above."
echo "Stopped. Data is kept; start again with ./dev-start.sh"
