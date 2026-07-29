#!/bin/sh

set -eu

backup_directory=${BACKUP_DIRECTORY:-/backups}
interval_seconds=${BACKUP_INTERVAL_SECONDS:-86400}
run_on_start=${BACKUP_RUN_ON_START:-true}

case "$interval_seconds" in
    ''|*[!0-9]*) echo "Invalid backup interval" >&2; exit 64 ;;
esac
[ "$interval_seconds" -ge 60 ] || { echo "Backup interval must be at least 60 seconds" >&2; exit 64; }

mkdir -p "$backup_directory/daily" "$backup_directory/weekly"
chown -R postgres:postgres "$backup_directory"
touch "$backup_directory/.operation.lock"
chown postgres:postgres "$backup_directory/.operation.lock"

case "${1:-loop}" in
    run-once)
        exec flock -w 60 "$backup_directory/.operation.lock" gosu postgres /usr/local/bin/backup-runner.sh backup
        ;;
    verify-latest)
        exec flock -w 60 "$backup_directory/.operation.lock" gosu postgres /usr/local/bin/backup-runner.sh verify-latest
        ;;
    test-retention)
        exec /usr/local/bin/test-retention.sh
        ;;
    loop)
        ;;
    *)
        echo "Unsupported backup command" >&2
        exit 64
        ;;
esac

while ! pg_isready -q; do
    sleep 2
done

if [ "$run_on_start" = "true" ]; then
    flock -w 60 "$backup_directory/.operation.lock" gosu postgres /usr/local/bin/backup-runner.sh backup || true
fi

while true; do
    sleep "$interval_seconds"
    flock -w 60 "$backup_directory/.operation.lock" gosu postgres /usr/local/bin/backup-runner.sh backup || true
done
