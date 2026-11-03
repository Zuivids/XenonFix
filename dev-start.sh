#!/usr/bin/env bash
# Starts the local system: database (Docker), backend (Spring Boot, "local" profile) and
# frontend (Angular with server rendering). Creates .env with random passwords on the first run.
# Logs: logs/backend.log, logs/frontend.log. Stop everything with ./dev-stop.sh
set -euo pipefail
cd "$(dirname "$0")"

fail() { echo "ERROR: $*" >&2; exit 1; }
random() { LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 24 || true; }
port_in_use() { curl -s -o /dev/null --max-time 2 "http://localhost:$1"; }

# Waits up to 3 minutes for the URL to answer 200
wait_for() {
  for _ in $(seq 1 180); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$1")" = "200" ] && return 0
    sleep 1
  done
  return 1
}

command -v docker >/dev/null || fail "Docker is not installed."
if ! docker info >/dev/null 2>&1; then
  # Docker Desktop takes a while to start after login; wait up to 90 seconds
  echo "Waiting for Docker Desktop to start..."
  for _ in $(seq 1 45); do docker info >/dev/null 2>&1 && break; sleep 2; done
  docker info >/dev/null 2>&1 || fail "Docker is not running. Start Docker Desktop and try again."
fi
command -v npm >/dev/null || fail "Node.js (npm) is not installed."
command -v java >/dev/null || fail "Java 21 is not installed."

if [ ! -f .env ]; then
  echo "Creating .env with random local passwords..."
  sed -e "s/^DB_PASSWORD=.*/DB_PASSWORD=$(random)/" \
      -e "s/^DB_ROOT_PASSWORD=.*/DB_ROOT_PASSWORD=$(random)/" \
      .env.example > .env
fi

mkdir -p logs

echo "Starting database (container xenonfix-mariadb)..."
docker compose up -d --wait --wait-timeout 180 || { docker compose logs --tail 15 mariadb; fail "The database did not start. See the lines above."; }

if port_in_use 8090; then
  echo "Backend is already running on port 8090."
else
  echo "Starting backend (first start compiles the code, about a minute)..."
  (cd backend && nohup bash ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=local >../logs/backend.log 2>&1 &)
fi

if port_in_use 4300; then
  echo "Frontend is already running on port 4300."
else
  if [ ! -d frontend/node_modules ]; then
    echo "Installing frontend packages..."
    (cd frontend && npm ci --no-fund)
  fi
  echo "Starting frontend..."
  (cd frontend && nohup npm start >../logs/frontend.log 2>&1 &)
fi

echo "Waiting for the backend..."
wait_for http://localhost:8090/api/health || fail "Backend did not start. See logs/backend.log"

echo "Waiting for the frontend..."
wait_for http://localhost:4300/ || fail "Frontend did not start. See logs/frontend.log"

cat <<EOF

XenonFix is running
  Site:      http://localhost:4300  (also /ru and /en)
  Health:    http://localhost:8090/api/health
  Database:  localhost:3308, database xenonfix, login in .env
  Logs:      logs/backend.log, logs/frontend.log
  Stop:      ./dev-stop.sh
EOF
