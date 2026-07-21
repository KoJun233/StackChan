# PostgreSQL External Volume Migration

This runbook moves the local StackChan PostgreSQL database from a Compose-managed anonymous volume to the external volume `stackchan-postgres-data`. Run the commands from the repository root in PowerShell.

## Safety requirements

- Keep the existing `COMPANION_SECRETS_ENCRYPTION_KEY` unchanged. The stored LLM provider key cannot be decrypted if this value changes.
- Record the pre-migration and post-migration row counts for `admin_users`, `devices`, `llm_provider_settings`, `conversations`, and `conversation_messages`; the counts must match.
- Do not delete the verified dump or the old anonymous PostgreSQL volume until the restore, application checks, and restart-survival checks all pass.
- Never print or commit `.env` values, passwords, API keys, device tokens, encryption keys, or dump contents.

## Create and verify the backup

Create a custom-format dump inside the running PostgreSQL container, copy it outside the repository, and store only its path in `.tools\latest-db-dump.txt`.

```powershell
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$fileName = "stackchan-$timestamp.dump"
$containerPath = "/tmp/$fileName"
$backupDirectory = Join-Path $env:USERPROFILE 'Documents\stackchan-backups'
$dumpPath = Join-Path $backupDirectory $fileName

New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
docker compose exec -T postgres pg_dump -U stackchan -d stackchan -Fc -f $containerPath
$postgresId = docker compose ps -q postgres
docker cp "${postgresId}:$containerPath" $dumpPath
```

Write `$dumpPath` to `.tools\latest-db-dump.txt` without adding that file to Git. Verify the dump with the PostgreSQL image pinned in `compose.yaml`; suppress the object listing so dump contents are not printed.

```powershell
$dumpPath = (Get-Content '.tools\latest-db-dump.txt' -Raw).Trim()
$dumpDirectory = Split-Path -Parent $dumpPath
$dumpName = Split-Path -Leaf $dumpPath
$postgresImage = 'postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4'

docker run --rm --mount "type=bind,source=$dumpDirectory,target=/backup,readonly" $postgresImage pg_restore --list "/backup/$dumpName" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Dump verification failed' }
```

## Record row counts

Run this before migration and again after restore and after the restart-survival test. Preserve the actual counts and require the before/after values to match.

```powershell
$tables = @('admin_users', 'devices', 'llm_provider_settings', 'conversations', 'conversation_messages')
foreach ($table in $tables) {
  $count = docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U stackchan -d stackchan -Atc "SELECT count(*) FROM $table;"
  "$table=$count"
}
```

## Create the external volume and restore

Validate the rendered Compose configuration before stopping services. Remove containers without `-v`, which leaves the old anonymous volume available for recovery.

```powershell
docker volume create stackchan-postgres-data
docker compose config --quiet

docker compose stop server redis postgres
docker compose rm -f server redis postgres
docker compose up -d postgres

$postgresId = docker compose ps -q postgres
if (-not $postgresId) { throw 'PostgreSQL container was not created' }

$readinessDeadline = (Get-Date).AddSeconds(90)
do {
  docker compose exec -T postgres pg_isready -U stackchan -d stackchan -t 3 | Out-Null
  if ($LASTEXITCODE -eq 0) { break }
  if ((Get-Date) -ge $readinessDeadline) { throw 'PostgreSQL did not become ready within 90 seconds' }
  Start-Sleep -Seconds 2
} while ($true)

$dumpPath = (Get-Content '.tools\latest-db-dump.txt' -Raw).Trim()
docker cp $dumpPath "${postgresId}:/tmp/stackchan.dump"
docker compose exec -T postgres pg_restore -U stackchan -d stackchan --clean --if-exists --exit-on-error /tmp/stackchan.dump

docker compose up -d redis server
```

Wait for PostgreSQL and the server to become healthy, compare the row counts, and verify login plus AI configuration metadata access without printing any credentials, provider keys, device tokens, or encrypted values.

## Verify survival of `down -v`

Because the volume is external, Compose must not remove it.

```powershell
docker compose down -v
docker volume inspect stackchan-postgres-data | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'External PostgreSQL volume was removed' }

docker compose up -d
```

After startup, run the row-count comparison and application checks once more. Keep both the verified dump and the old anonymous volume until every check has passed and recovery is no longer required.
