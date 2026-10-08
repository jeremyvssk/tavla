#!/usr/bin/env sh
# One-step start. Docker is the only prerequisite; the Stripe CLI runs in a container too.
#
#   ./start.sh          build and run the whole stack
#   ./start.sh down     stop it
#   ./start.sh reset    stop it and delete the volumes (wipes the database)
set -eu

cd "$(dirname "$0")"

# Compose reads .env automatically. docker-compose.yml carries dev defaults for every
# variable but one, so this file is mostly about giving you one place to edit.
if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example."
fi

# The one without a default: the AES-256 key that encrypts order and payment details in the database.
# Random, made once per machine, kept only in .env (never in git). Lose it and the stored orders can't be
# read, so back .env up with the database. Done before any compose command, since compose refuses to run without it.
key=$(sed -n 's/^APP_ENCRYPTION_KEY=//p' .env | tail -n 1)
if [ -z "$key" ]; then
  key=$(head -c 32 /dev/urandom | base64 | tr -d '\n')
  grep -v '^APP_ENCRYPTION_KEY=' .env > .env.tmp || true
  echo "APP_ENCRYPTION_KEY=$key" >> .env.tmp
  mv .env.tmp .env
  echo "Created an encryption key in .env (APP_ENCRYPTION_KEY). Keep it: the stored orders can't be read without it."
fi

case "${1:-up}" in
  down)  docker compose down; exit 0 ;;
  reset) docker compose down -v; exit 0 ;;
  up)    ;;
  *)     echo "usage: $0 [up|down|reset]" >&2; exit 2 ;;
esac

# Payments: the stripe-cli container forwards Stripe's test events to the backend, signed with a
# secret of its own. Ask the CLI for that secret and give it to the backend, so the webhook trusts it.
stripe_key=$(sed -n 's/^STRIPE_SECRET_KEY=//p' .env | tail -n 1)
if [ -n "$stripe_key" ]; then
  secret=$(docker compose run --rm --no-deps stripe-cli listen --print-secret 2>/dev/null | tr -d '\r' | tail -n 1)
  case "$secret" in
    whsec_*)
      grep -v '^STRIPE_WEBHOOK_SECRET=' .env > .env.tmp
      echo "STRIPE_WEBHOOK_SECRET=$secret" >> .env.tmp
      mv .env.tmp .env ;;
    *) echo "Could not get the webhook secret from the Stripe CLI. Is STRIPE_SECRET_KEY an sk_test_ key?" >&2 ;;
  esac
else
  echo "No STRIPE_SECRET_KEY in .env: the shop runs, but checkout can't take payments. See README, Setup."
fi

docker compose up --build -d

cat <<'BANNER'

  Stack is up.

    App          https://localhost:5443   (self-signed certificate: accept the warning once;
                                           http://localhost:5173 redirects here)
    API          http://localhost:8080
    Mail (dev)   http://localhost:8025
    RabbitMQ     http://localhost:15672   (app / changeme: queues and dead letter queues)
    Stripe       docker compose logs -f stripe-cli   (webhook events as they arrive)
    Postgres     localhost:5432
    Redis        localhost:6379

  Logs:  docker compose logs -f backend
  Stop:  ./start.sh down

BANNER
