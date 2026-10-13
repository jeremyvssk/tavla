#!/usr/bin/env sh
# Load test: starts a separate copy of the stack, runs shop.js at each user count for a minute, samples
# docker stats alongside, and writes a summary table. Docker is the only thing it needs.
#
#   loadtest/run.sh                    the default steps: 10 25 50 100 200 300 400 users
#   loadtest/run.sh 50 150             only these
#   DURATION=120s loadtest/run.sh 50   longer steps
#   loadtest/run.sh down               stop the copy and delete its volumes
set -eu
cd "$(dirname "$0")"

P=ils-load
ENV=.env.loadtest
K6=grafana/k6:1.3.0

# Its own key and its own host ports, so it runs beside your normal stack. Gitignored.
if [ ! -f "$ENV" ]; then
  {
    echo "APP_ENCRYPTION_KEY=$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
    echo "POSTGRES_PORT=15432"; echo "REDIS_PORT=16379"; echo "MAILHOG_SMTP_PORT=11025"; echo "MAILHOG_UI_PORT=18025"
    echo "RABBITMQ_UI_PORT=25672"; echo "BACKEND_PORT=18080"; echo "FRONTEND_PORT=15180"; echo "FRONTEND_HTTPS_PORT=15443"
  } > "$ENV"
fi
dc() { docker compose -p "$P" --env-file "$ENV" -f ../docker-compose.yml -f docker-compose.loadtest.yml "$@"; }

if [ "${1:-}" = "down" ]; then dc down -v; exit 0; fi

dc up --build -d postgres redis rabbitmq mailhog backend frontend
printf 'Waiting for the shop'
until curl -ksf -o /dev/null https://localhost:15443/categories; do printf '.'; sleep 3; done
echo ' up.'

LEVELS=${*:-10 25 50 100 200 300 400}
OUT="results/$(date +%Y-%m-%d-%H%M)"
mkdir -p "$OUT"
{
  echo "date: $(date -Iseconds)"
  echo "host: $(uname -sr), $(docker info --format '{{.NCPU}} CPUs, {{.MemTotal}} bytes for Docker')"
  echo "backend: 2 CPUs, 1 GB (docker-compose.loadtest.yml); step length ${DURATION:-60s}"
} > "$OUT/machine.txt"

for vus in $LEVELS; do
  echo "== $vus users"
  # docker stats takes about two seconds per sample; it runs until k6 finishes
  ( while true; do
      docker stats --no-stream --format "{{.Name}},{{.CPUPerc}},{{.MemPerc}},{{.MemUsage}}" | grep "^$P-" | sed "s/^/$(date +%s),/"
    done ) > "$OUT/stats-$vus.csv" 2>/dev/null &
  SAMPLER=$!
  docker run --rm --network "${P}_default" -v "$PWD":/scripts -e VUS="$vus" -e DURATION="${DURATION:-60s}" \
    "$K6" run --quiet --summary-export "/scripts/$OUT/k6-$vus.json" /scripts/shop.js > "$OUT/k6-$vus.log" 2>&1 || true
  kill "$SAMPLER" 2>/dev/null || true
  tail -n 40 "$OUT/k6-$vus.log" | grep -E "http_req_duration|http_reqs|transactions|journey_ok|http_req_failed" || true
  # let orders from this step expire and the JVM settle before the next
  sleep 20
done

docker run --rm -v "$PWD":/w python:3.13-alpine python /w/summarize.py "/w/$OUT" | tee "$OUT/summary.md"
echo "Results in loadtest/$OUT. Stop the copy with: loadtest/run.sh down"
