param([switch]$AllowTemporaryAdministrator)

# Credentials live only in memory and container stdin. No business writes or motion tests.
$ErrorActionPreference = 'Stop'
if (!$AllowTemporaryAdministrator) {
    throw 'Temporary administrator creation needs explicit user approval and -AllowTemporaryAdministrator.'
}
$rawSource = & docker.exe inspect stackchan-foundation-server-1 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Expected LAN server inspection failed; private output suppressed.' }
$source = (($rawSource -join "`n") | ConvertFrom-Json)[0]
if ($source.Config.Labels.'com.docker.compose.project' -ne 'stackchan-foundation' `
    -or $source.Config.Env -notcontains 'COMPANION_LAN_DEVELOPMENT=true' `
    -or $source.Config.Env -contains 'COMPANION_PRODUCTION=true') {
    throw 'Headless acceptance is restricted to the existing LAN development environment.'
}
$rawSource = $null
$source = $null
$image = 'mcr.microsoft.com/playwright@sha256:eff16c30e6f3f4af0a03fa4b706120d5e9b0891c344a27d64559aff5900a4a27'
$databaseContainer = 'stackchan-foundation-postgres-1'
$container = 'companion-headless-' + [guid]::NewGuid().ToString('N')
$root = Split-Path $PSScriptRoot -Parent
$output = Join-Path $root ('.tools/' + $container)
$runner = Join-Path $PSScriptRoot 'verify-companion-console-headless.cjs'
$adminId = [guid]::NewGuid().ToString()
$adminName = 'companion-headless-once-' + [guid]::NewGuid().ToString('N')
$password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$salt = [Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
$derived = [Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2(
    $password, $salt, 310000, [Security.Cryptography.HashAlgorithmName]::SHA256, 32
)
$hash = '{pbkdf2@SpringSecurity_v5_8}' + [Convert]::ToHexString([byte[]]($salt + $derived)).ToLowerInvariant()
$created = $false
$failure = $null

function QueryDatabase([string]$Sql) {
    $result = $Sql | & docker.exe exec -i $databaseContainer psql -XAt -v ON_ERROR_STOP=1 -U stackchan -d stackchan 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Headless test database operation failed; private output suppressed.' }
    return @($result)
}

try {
    $null = New-Item -ItemType Directory -Path $output
    $null = QueryDatabase "insert into admin_users(id, username, password_hash, created_at) values ('$adminId', '$adminName', '$hash', now());"
    $created = $true
    $inputJson = @{ username = $adminName; password = $password } | ConvertTo-Json -Compress
    $inputJson | & docker.exe run --rm --init -i --name $container --cpus 1 --memory 1g --shm-size 512m `
        --mount "type=bind,source=$runner,target=/test/runner.cjs,readonly" `
        --mount "type=bind,source=$output,target=/evidence" `
        $image bash -lc 'npm install --prefix /tmp/headless --no-audit --no-fund playwright@1.63.0 >/tmp/install.log 2>&1 && NODE_PATH=/tmp/headless/node_modules node /test/runner.cjs'
    if ($LASTEXITCODE -ne 0) { throw "Headless browser test failed (exit $LASTEXITCODE); see sanitized evidence." }
    Write-Output "Headless evidence: $output"
}
catch {
    $failure = $_
}
finally {
    $null = & docker.exe rm -f $container 2>&1
    if ($created) {
        $null = QueryDatabase "delete from admin_users where id = '$adminId' and username = '$adminName';"
        if (@(QueryDatabase "select id from admin_users where id = '$adminId';").Count -ne 0) {
            throw 'Temporary headless administrator cleanup failed.'
        }
        Write-Output 'Temporary headless administrator removed.'
    }
    $password = $null
    $hash = $null
    $inputJson = $null
}
if ($failure) { throw $failure }
