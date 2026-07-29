#!/bin/sh

set -eu

. /usr/local/bin/backup-lib.sh

backup_root=${BACKUP_DIRECTORY:-/backups}
daily_directory="$backup_root/daily"
weekly_directory="$backup_root/weekly"
mkdir -p "$daily_directory" "$weekly_directory"

temporary_cluster=''
temporary_socket=''
temporary_port=''

cleanup() {
    if [ -n "$temporary_cluster" ] && [ -d "$temporary_cluster" ]; then
        pg_ctl -D "$temporary_cluster" -m immediate stop >/dev/null 2>&1 || true
        rm -rf -- "$temporary_cluster"
    fi
    if [ -n "$temporary_socket" ] && [ -d "$temporary_socket" ]; then
        rm -rf -- "$temporary_socket"
    fi
    temporary_cluster=''
    temporary_socket=''
    temporary_port=''
}
trap cleanup EXIT HUP INT TERM

now_utc() {
    date -u '+%Y-%m-%dT%H:%M:%SZ'
}

safe_database_counts() {
    psql "$@" -X -A -t -v ON_ERROR_STOP=1 -c "
        select json_build_object(
          'persona', (select count(*) from companion_persona_settings),
          'confirmedMemories', (select count(*) from long_term_memories where confirmation_status = 'CONFIRMED'),
          'reminders', (select count(*) from reminders),
          'expressionPacks', (select count(*) from expression_packs)
        );"
}

schema_versions() {
    psql "$@" -X -A -t -v ON_ERROR_STOP=1 -c "
        select coalesce(json_agg(version order by installed_rank) filter (where success), '[]'::json)
          from flyway_schema_history;"
}

verify_dump() {
    dump_path="$1"
    expected_counts="$2"
    verification_time=$(now_utc)
    temporary_cluster=$(mktemp -d /tmp/stackchan-restore-data.XXXXXX)
    temporary_socket=$(mktemp -d /tmp/stackchan-restore-socket.XXXXXX)
    temporary_port=$((55432 + ($$ % 1000)))

    initdb -D "$temporary_cluster" --no-locale --encoding=UTF8 --auth=trust >/dev/null \
        || { cleanup; return 1; }
    pg_ctl -D "$temporary_cluster" -o "-k $temporary_socket -p $temporary_port -c listen_addresses=''" -w start >/dev/null \
        || { cleanup; return 1; }
    createdb -h "$temporary_socket" -p "$temporary_port" -U postgres stackchan_restore \
        || { cleanup; return 1; }
    pg_restore -h "$temporary_socket" -p "$temporary_port" -U postgres -d stackchan_restore \
        --no-owner --no-privileges --exit-on-error "$dump_path" \
        || { cleanup; return 1; }
    restored_counts=$(safe_database_counts -h "$temporary_socket" -p "$temporary_port" -U postgres -d stackchan_restore) \
        || { cleanup; return 1; }

    if [ "$restored_counts" != "$expected_counts" ]; then
        cleanup
        return 1
    fi
    cleanup
    printf '%s' "$verification_time"
}

write_failure_status() {
    failure_code="$1"
    failure_time=$(now_utc)
    previous_success=$(status_string_or_null lastSuccessfulBackupAt)
    if [ "$failure_code" = "RESTORE_VERIFICATION_FAILED" ]; then
        previous_restore_time="\"$failure_time\""
        previous_restore_success=false
        previous_restore_failure='"RESTORE_VERIFICATION_FAILED"'
    else
        previous_restore_time=$(status_string_or_null lastRestoreVerificationAt)
        previous_restore_success=$(status_boolean_or_null lastRestoreVerificationSuccessful)
        previous_restore_failure=$(status_string_or_null lastRestoreVerificationFailureCode)
    fi
    atomic_write_status "$backup_root" "{\"schemaVersion\":1,\"lastAttemptAt\":\"$failure_time\",\"lastSuccessfulBackupAt\":$previous_success,\"lastFailureAt\":\"$failure_time\",\"lastFailureCode\":\"$failure_code\",\"lastRestoreVerificationAt\":$previous_restore_time,\"lastRestoreVerificationSuccessful\":$previous_restore_success,\"lastRestoreVerificationFailureCode\":$previous_restore_failure}"
}

status_string_or_null() {
    field="$1"
    status_path="$backup_root/status.json"
    [ -f "$status_path" ] || { printf 'null'; return; }
    value=$(sed -n "s/.*\"$field\":\"\([^\"]*\)\".*/\1/p" "$status_path")
    if [ -n "$value" ]; then
        printf '"%s"' "$value"
    else
        printf 'null'
    fi
}

status_boolean_or_null() {
    field="$1"
    status_path="$backup_root/status.json"
    [ -f "$status_path" ] || { printf 'null'; return; }
    value=$(sed -n "s/.*\"$field\":\(true\|false\).*/\1/p" "$status_path")
    if [ -n "$value" ]; then
        printf '%s' "$value"
    else
        printf 'null'
    fi
}

run_backup() {
    attempt_time=$(now_utc)
    timestamp=$(date -u '+%Y%m%dT%H%M%SZ')
    week_key=$(date -u '+%G-W%V')
    daily_base="$daily_directory/daily-$timestamp"
    partial_dump="$daily_base.dump.partial"
    final_dump="$daily_base.dump"
    final_manifest="$daily_base.manifest.json"

    rm -f -- "$partial_dump"
    source_counts=$(safe_database_counts) || { write_failure_status SOURCE_COUNT_FAILED; return 1; }
    source_schema_versions=$(schema_versions) || { write_failure_status SCHEMA_READ_FAILED; return 1; }
    source_database_version=$(psql -X -A -t -v ON_ERROR_STOP=1 -c "show server_version_num") \
        || { write_failure_status VERSION_READ_FAILED; return 1; }

    if ! pg_dump -Fc --no-owner --no-privileges --file="$partial_dump"; then
        rm -f -- "$partial_dump"
        write_failure_status DUMP_FAILED
        return 1
    fi
    dump_sha256=$(sha256sum "$partial_dump" | awk '{print $1}')
    dump_size=$(wc -c < "$partial_dump" | tr -d ' ')
    mv -- "$partial_dump" "$final_dump"

    verification_time=$(verify_dump "$final_dump" "$source_counts") \
        || { rm -f -- "$final_dump"; write_failure_status RESTORE_VERIFICATION_FAILED; return 1; }

    manifest_partial="$final_manifest.partial"
    printf '%s\n' "{\"schemaVersion\":1,\"createdAt\":\"$attempt_time\",\"sizeBytes\":$dump_size,\"sha256\":\"$dump_sha256\",\"databaseVersion\":\"$source_database_version\",\"flywaySchemaVersions\":$source_schema_versions,\"recordCounts\":$source_counts}" > "$manifest_partial"
    mv -- "$manifest_partial" "$final_manifest"

    weekly_base="$weekly_directory/week-$week_key"
    weekly_dump_partial="$weekly_base.dump.partial"
    weekly_manifest_partial="$weekly_base.manifest.json.partial"
    cp -- "$final_dump" "$weekly_dump_partial"
    cp -- "$final_manifest" "$weekly_manifest_partial"
    rm -f -- "$weekly_base.manifest.json"
    mv -f -- "$weekly_dump_partial" "$weekly_base.dump"
    mv -f -- "$weekly_manifest_partial" "$weekly_base.manifest.json"

    rotate_backup_sets "$daily_directory" 'daily-' 7
    rotate_backup_sets "$weekly_directory" 'week-' 4
    previous_failure_time=$(status_string_or_null lastFailureAt)
    previous_failure_code=$(status_string_or_null lastFailureCode)
    atomic_write_status "$backup_root" "{\"schemaVersion\":1,\"lastAttemptAt\":\"$attempt_time\",\"lastSuccessfulBackupAt\":\"$attempt_time\",\"lastFailureAt\":$previous_failure_time,\"lastFailureCode\":$previous_failure_code,\"lastRestoreVerificationAt\":\"$verification_time\",\"lastRestoreVerificationSuccessful\":true,\"lastRestoreVerificationFailureCode\":null}"
}

verify_latest() {
    set -- "$daily_directory"/daily-*.manifest.json
    [ -e "$1" ] || { echo "No successful backup is available" >&2; exit 66; }
    latest_manifest=$(printf '%s\n' "$@" | sort | tail -n 1)
    latest_dump=${latest_manifest%.manifest.json}.dump
    [ -f "$latest_dump" ] || { echo "Latest backup dump is missing" >&2; exit 66; }
    expected_sha=$(sed -n 's/.*"sha256":"\([a-f0-9]*\)".*/\1/p' "$latest_manifest")
    actual_sha=$(sha256sum "$latest_dump" | awk '{print $1}')
    [ -n "$expected_sha" ] && [ "$expected_sha" = "$actual_sha" ] \
        || { echo "Latest backup checksum is invalid" >&2; exit 65; }
    expected_counts=$(sed -n 's/.*"recordCounts":\({[^}]*}\).*/\1/p' "$latest_manifest")
    verification_time=$(verify_dump "$latest_dump" "$expected_counts") \
        || { write_failure_status RESTORE_VERIFICATION_FAILED; exit 1; }
    backup_time=$(sed -n 's/.*"createdAt":"\([^"]*\)".*/\1/p' "$latest_manifest")
    previous_attempt=$(status_string_or_null lastAttemptAt)
    previous_failure_time=$(status_string_or_null lastFailureAt)
    previous_failure_code=$(status_string_or_null lastFailureCode)
    atomic_write_status "$backup_root" "{\"schemaVersion\":1,\"lastAttemptAt\":$previous_attempt,\"lastSuccessfulBackupAt\":\"$backup_time\",\"lastFailureAt\":$previous_failure_time,\"lastFailureCode\":$previous_failure_code,\"lastRestoreVerificationAt\":\"$verification_time\",\"lastRestoreVerificationSuccessful\":true,\"lastRestoreVerificationFailureCode\":null}"
}

case "${1:-}" in
    backup) run_backup ;;
    verify-latest) verify_latest ;;
    *) echo "Unsupported backup runner operation" >&2; exit 64 ;;
esac
