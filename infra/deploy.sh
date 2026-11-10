#!/usr/bin/env bash
# Deploys MS06 on this VM.  Usage: bash infra/deploy.sh test|prod
# Required variables (the GitHub workflow sets them from the Environment):
#   DB_PASSWORD, GATEWAY_URL, GATEWAY_SERVICE_KEY
# Optional: GATEWAY_SYSTEM_USER_ID, SCHEDULING_ENABLED (default false)
set -euo pipefail

ENV_NAME="${1:-}"
if [[ "$ENV_NAME" != "test" && "$ENV_NAME" != "prod" ]]; then
  echo "Usage: bash infra/deploy.sh test|prod" >&2
  exit 1
fi
for var in DB_PASSWORD GATEWAY_URL GATEWAY_SERVICE_KEY; do
  if [[ -z "${!var:-}" ]]; then
    echo "Missing variable: $var" >&2
    exit 1
  fi
done

export ENV="$ENV_NAME"
cd "$(dirname "$0")"

# The edge (Caddy) creates this network; without it nobody can reach MS06.
if ! docker network inspect campus-edge >/dev/null 2>&1; then
  echo "The Docker network 'campus-edge' does not exist: start the Caddy edge first." >&2
  exit 1
fi

docker compose -p "ms06-$ENV" -f docker-compose.prod.yml up -d --build

echo "Waiting for http://ms06-$ENV:8086/actuator/health ..."
for attempt in $(seq 1 30); do
  if docker run --rm --network campus-edge curlimages/curl:8.11.1 -fsS \
      "http://ms06-$ENV:8086/actuator/health" | grep -q '"status":"UP"'; then
    echo "MS06 ($ENV) is UP."
    # Free disk space: remove old images that nothing uses.
    docker image prune -f >/dev/null
    exit 0
  fi
  sleep 5
done

echo "MS06 ($ENV) did not answer UP in time. Last logs:" >&2
docker logs --tail 80 "ms06-$ENV" >&2 || true
exit 1
