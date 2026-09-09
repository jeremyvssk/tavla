#!/usr/bin/env sh
# One-step start. Docker is the only prerequisite.
#
#   ./start.sh          build and run the whole stack
#   ./start.sh down     stop it
#   ./start.sh reset    stop it and delete the volumes (wipes the database)
set -eu

cd "$(dirname "$0")"

case "${1:-up}" in
  down)  docker compose down; exit 0 ;;
  reset) docker compose down -v; exit 0 ;;
  up)    ;;
  *)     echo "usage: $0 [up|down|reset]" >&2; exit 2 ;;
esac

# Compose reads .env automatically. docker-compose.yml carries dev defaults for every
# variable, so this file is about giving you one place to edit — not about booting.
if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example."
fi

docker compose up --build -d

cat <<'BANNER'

  Stack is up.

    App          http://localhost:5173
    API          http://localhost:8080
    Mail (dev)   http://localhost:8025
    Postgres     localhost:5432
    Redis        localhost:6379

  Logs:  docker compose logs -f backend
  Stop:  ./start.sh down

BANNER
