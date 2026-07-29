#!/bin/sh

set -eu

. /usr/local/bin/backup-lib.sh

test_root=$(mktemp -d /tmp/stackchan-retention-test.XXXXXX)
trap 'rm -rf -- "$test_root"' EXIT HUP INT TERM

create_set() {
    directory="$1"
    name="$2"
    : > "$directory/$name.dump"
    printf '{}\n' > "$directory/$name.manifest.json"
}

mkdir -p "$test_root/daily" "$test_root/weekly"
for timestamp in \
    20261228T000000Z 20261229T000000Z 20261230T000000Z 20261231T000000Z \
    20270101T000000Z 20270102T000000Z 20270103T000000Z 20270104T000000Z 20270105T000000Z; do
    create_set "$test_root/daily" "daily-$timestamp"
done
rotate_backup_sets "$test_root/daily" 'daily-' 7
[ "$(find "$test_root/daily" -name '*.manifest.json' | wc -l)" -eq 7 ]
[ ! -e "$test_root/daily/daily-20261228T000000Z.dump" ]
[ -e "$test_root/daily/daily-20270105T000000Z.dump" ]

for week in 2026-W52 2026-W53 2027-W01 2027-W02 2027-W03; do
    create_set "$test_root/weekly" "week-$week"
done
rotate_backup_sets "$test_root/weekly" 'week-' 4
[ "$(find "$test_root/weekly" -name '*.manifest.json' | wc -l)" -eq 4 ]
[ ! -e "$test_root/weekly/week-2026-W52.dump" ]

create_set "$test_root/weekly" 'week-2027-W03'
rotate_backup_sets "$test_root/weekly" 'week-' 4
[ "$(find "$test_root/weekly" -name '*.manifest.json' | wc -l)" -eq 4 ]

: > "$test_root/daily/daily-20270106T000000Z.dump.partial"
rotate_backup_sets "$test_root/daily" 'daily-' 7
[ -e "$test_root/daily/daily-20270106T000000Z.dump.partial" ]
