#!/bin/sh

set -eu

rotate_backup_sets() {
    target_directory="$1"
    prefix="$2"
    keep_count="$3"

    [ -d "$target_directory" ] || return 0
    set -- "$target_directory"/"$prefix"*.manifest.json
    [ -e "$1" ] || return 0

    manifest_count=$#
    remove_count=$((manifest_count - keep_count))
    [ "$remove_count" -gt 0 ] || return 0

    for manifest_path in $(printf '%s\n' "$@" | sort | head -n "$remove_count"); do
        base_path=${manifest_path%.manifest.json}
        rm -f -- "$base_path.dump" "$manifest_path"
    done
}

atomic_write_status() {
    backup_root="$1"
    content="$2"
    temporary_status="$backup_root/.status.json.partial.$$"
    printf '%s\n' "$content" > "$temporary_status"
    mv -f -- "$temporary_status" "$backup_root/status.json"
}
