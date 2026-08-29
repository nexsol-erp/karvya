#!/usr/bin/env bash
#
# Deploys the current main branch on the instance.
#
# Run on the server, either by hand over SSH or by the Deploy workflow. It is
# safe to run repeatedly: nothing here touches the database or media volumes.
#
# It does not roll back. Compose replaces the containers before anything can be
# checked, so if the new revision is broken it is the one left running, and the
# site is down until someone acts. The failure path prints the previous commit
# and the command to return to it. Automatic rollback would need two stacks and
# a switch in front of them, which is a larger change than this deployment
# warrants - but do not read the health check as a safety net, because it is
# only a report.
#
#   sudo -u karvya /opt/karvya/scripts/deploy.sh

set -euo pipefail

APP_DIR="${APP_DIR:-/opt/karvya}"
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

cd "$APP_DIR"

if [[ ! -f .env ]]; then
    echo "No .env in $APP_DIR. Copy .env.example and fill it in before deploying." >&2
    exit 1
fi

previous="$(git rev-parse --short HEAD)"
echo "==> currently at $previous"

echo "==> fetching"
git fetch --quiet origin main
target="$(git rev-parse --short origin/main)"

if [[ "$previous" == "$target" ]]; then
    echo "==> already at $target; rebuilding anyway in case .env changed"
else
    echo "==> moving to $target"
fi

# Discards local edits on the server on purpose. The instance is not a place to
# edit code: anything changed here would be silently reverted by the next
# deploy, so it is better to make that explicit and immediate.
git reset --hard --quiet origin/main

echo "==> building"
"${COMPOSE[@]}" build

echo "==> starting"
# Flyway runs inside the backend on startup, so there is no separate migration
# step. The backend's own health check does not report ready until it has
# finished, which is what the wait below is watching for.
"${COMPOSE[@]}" up -d --remove-orphans

echo "==> waiting for readiness (up to ${HEALTH_TIMEOUT}s)"
deadline=$(( SECONDS + HEALTH_TIMEOUT ))
until curl -fsS --max-time 5 http://127.0.0.1/healthz >/dev/null 2>&1 \
   && "${COMPOSE[@]}" exec -T backend \
        wget -qO- http://127.0.0.1:8080/actuator/health/readiness 2>/dev/null \
        | grep -q '"status":"UP"'; do
    if (( SECONDS > deadline )); then
        echo "!! did not become ready in ${HEALTH_TIMEOUT}s" >&2
        "${COMPOSE[@]}" ps >&2
        "${COMPOSE[@]}" logs --tail=80 backend web >&2
        echo "!! previous revision was $previous; to go back:" >&2
        echo "     git reset --hard $previous && ${COMPOSE[*]} up -d --build" >&2
        exit 1
    fi
    sleep 3
done

echo "==> ready at $target"

# Images accumulate quickly with a rebuild on every push and will fill a small
# root volume. Only dangling ones, so a tagged image is never removed.
docker image prune -f >/dev/null

"${COMPOSE[@]}" ps
