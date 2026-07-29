param(
    [string]$EnvFile = '.env'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$resolvedEnvFile = (Resolve-Path (Join-Path $repositoryRoot $EnvFile) -ErrorAction Stop).Path

Push-Location $repositoryRoot
try {
    docker compose --env-file $resolvedEnvFile -f compose.yaml run --rm --no-deps postgres-backup verify-latest
    if ($LASTEXITCODE -ne 0) {
        throw 'Latest PostgreSQL backup restore verification failed.'
    }
}
finally {
    Pop-Location
}
