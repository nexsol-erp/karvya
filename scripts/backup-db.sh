#!/usr/bin/env bash
#
# Nightly database dump, kept locally and optionally copied to S3.
#
# The database and media volumes hold everything that cannot be rebuilt from
# the repository. An EC2 instance can be replaced in minutes; the orders on it
# cannot, so this is the one piece of the deployment that is not optional.
#
# Install as a cron entry, as the user that owns /opt/karvya:
#   15 2 * * * /opt/karvya/scripts/backup-db.sh >> /var/log/karvya-backup.log 2>&1

set -euo pipefail

APP_DIR="${APP_DIR:-/opt/karvya}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/karvya}"
KEEP_DAYS="${KEEP_DAYS:-14}"
# Leave empty to keep backups on the instance only. On its own that protects
# against a bad migration but not against losing the instance.
S3_BUCKET="${S3_BUCKET:-}"

cd "$APP_DIR"
set -a; . ./.env; set +a

mkdir -p "$BACKUP_DIR"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dump="$BACKUP_DIR/karvya-$stamp.sql.gz"

echo "==> dumping to $dump"
# Through the running container, so no postgres client is needed on the host
# and the credentials never appear in the process list.
docker compose exec -T \
    -e PGPASSWORD="$POSTGRES_PASSWORD" \
    db pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists \
    | gzip -9 > "$dump"

# A dump that failed halfway still leaves a file, and gzip is happy to compress
# nothing. Refuse to treat an implausibly small file as a backup.
size=$(stat -c%s "$dump")
if (( size < 4096 )); then
    echo "!! dump is only ${size} bytes, which cannot be right" >&2
    rm -f "$dump"
    exit 1
fi
echo "==> wrote ${size} bytes"

if [[ -n "$S3_BUCKET" ]]; then
    echo "==> copying to s3://$S3_BUCKET/"
    aws s3 cp "$dump" "s3://$S3_BUCKET/$(basename "$dump")" --only-show-errors
fi

echo "==> pruning local dumps older than ${KEEP_DAYS} days"
find "$BACKUP_DIR" -name 'karvya-*.sql.gz' -mtime "+$KEEP_DAYS" -delete

echo "==> done"
