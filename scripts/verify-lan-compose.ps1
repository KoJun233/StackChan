$ErrorActionPreference = 'Stop'

function Read-ComposeConfig([string[]]$Files) {
    $arguments = @('compose')
    foreach ($file in $Files) {
        $arguments += @('-f', $file)
    }
    $arguments += @('config', '--format', 'json')
    $json = & docker @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose config failed for $($Files -join ', ')"
    }
    return $json | ConvertFrom-Json
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

$base = Read-ComposeConfig @('compose.yaml')
$lan = Read-ComposeConfig @('compose.yaml', 'compose.lan.yaml')

$basePorts = @($base.services.server.ports)
$lanPorts = @($lan.services.server.ports)
Assert-True ($basePorts.Count -eq 1) 'Base Compose must publish exactly one server port'
Assert-True ($basePorts[0].host_ip -eq '127.0.0.1') 'Base Compose must bind server to 127.0.0.1'
Assert-True ([int]$basePorts[0].published -eq 8080) 'Base Compose published port must be 8080'
Assert-True ([int]$basePorts[0].target -eq 8080) 'Base Compose target port must be 8080'
Assert-True ($lanPorts.Count -eq 1) 'LAN Compose must publish exactly one server port'
Assert-True ($lanPorts[0].host_ip -eq '0.0.0.0') 'LAN Compose must bind server to 0.0.0.0'
Assert-True ([int]$lanPorts[0].published -eq 8080) 'LAN Compose published port must be 8080'
Assert-True ([int]$lanPorts[0].target -eq 8080) 'LAN Compose target port must be 8080'
Assert-True ($lan.services.server.environment.COMPANION_LAN_DEVELOPMENT -eq 'true') 'LAN mode must be true'
Assert-True ($lan.services.server.environment.COMPANION_PRODUCTION -eq 'false') 'Production mode must be false'
Assert-True ($lan.services.server.environment.SERVER_SERVLET_SESSION_COOKIE_SECURE -eq 'false') 'LAN cookie Secure must be false'
Assert-True (@($base.services.postgres.ports | Where-Object { $null -ne $_ }).Count -eq 0) 'PostgreSQL must not publish a host port'
Assert-True (@($base.services.redis.ports | Where-Object { $null -ne $_ }).Count -eq 0) 'Redis must not publish a host port'
Assert-True (@($lan.services.postgres.ports | Where-Object { $null -ne $_ }).Count -eq 0) 'LAN PostgreSQL must not publish a host port'
Assert-True (@($lan.services.redis.ports | Where-Object { $null -ne $_ }).Count -eq 0) 'LAN Redis must not publish a host port'

Write-Output 'Compose LAN verification passed'
