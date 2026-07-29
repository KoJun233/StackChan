FROM postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4

COPY ops/postgres-backup/backup-lib.sh /usr/local/bin/backup-lib.sh
COPY ops/postgres-backup/backup-runner.sh /usr/local/bin/backup-runner.sh
COPY ops/postgres-backup/entrypoint.sh /usr/local/bin/stackchan-backup-entrypoint.sh
COPY ops/postgres-backup/test-retention.sh /usr/local/bin/test-retention.sh

RUN chmod 0555 /usr/local/bin/backup-lib.sh \
        /usr/local/bin/backup-runner.sh \
        /usr/local/bin/stackchan-backup-entrypoint.sh \
        /usr/local/bin/test-retention.sh \
    && /usr/local/bin/test-retention.sh

ENTRYPOINT ["/usr/local/bin/stackchan-backup-entrypoint.sh"]
