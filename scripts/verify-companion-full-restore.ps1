param(
    [string]$CandidateImage = 'stackchan-foundation-server:companion-v51-20260914',
    [string]$SourceServer = 'stackchan-foundation-server-1',
    [string]$SourceBackup = 'stackchan-foundation-postgres-backup-1'
)

# Requires PowerShell 7 on Windows. Never restores over an existing database.
# The preserved configuration is Windows DPAPI protected, not portable plaintext.
$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$bundle = "stackchan-release-$stamp"
$restore = "stackchan-restore-$stamp"
$pgImage = 'postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4'
$createdContainers = [System.Collections.Generic.List[string]]::new()
$createdVolumes = [System.Collections.Generic.List[string]]::new()
$networkCreated = $false

function Docker([string[]]$Arguments, [string]$InputText = '') {
    if ($InputText) { $result = $InputText | & docker.exe @Arguments 2>&1 }
    else { $result = & docker.exe @Arguments 2>&1 }
    if ($LASTEXITCODE -ne 0) { throw "Docker operation failed: $($Arguments[0]); private output suppressed." }
    return ($result -join "`n")
}

function RestoreSql([string]$Query) {
    Docker @('exec', '-i', "$restore-pg", 'psql', '-XAt', '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', 'stackchan') $Query
}

try {
    $source = (Docker @('inspect', $SourceServer) | ConvertFrom-Json)[0]
    $backupSource = (Docker @('inspect', $SourceBackup) | ConvertFrom-Json)[0]
    $skillMount = @($source.Mounts | Where-Object Destination -eq '/app/data/agent-skills')
    $backupMount = @($backupSource.Mounts | Where-Object Destination -eq '/backups')
    if ($skillMount.Count -ne 1 -or $backupMount.Count -ne 1 -or $skillMount[0].Type -ne 'volume' -or $backupMount[0].Type -ne 'volume') {
        throw 'Expected named Skill and backup volumes were not found.'
    }
    $null = Docker @('image', 'inspect', $CandidateImage)
    $null = Docker @('exec', '--user', 'postgres', $SourceBackup, '/usr/local/bin/backup-runner.sh', 'backup')
    $null = Docker @('volume', 'create', '--label', 'stackchan.purpose=release-backup', $bundle)
    $null = Docker @('run', '--rm', '--network', 'none', '--mount', "type=volume,src=$($backupMount[0].Name),dst=/source,readonly", '--mount', "type=volume,src=$($skillMount[0].Name),dst=/skills,readonly", '--mount', "type=volume,src=$bundle,dst=/bundle", $pgImage, 'sh', '-ec', 'umask 077; latest=$(find /source/daily -name "daily-*.manifest.json" | sort | tail -n 1); test -n "$latest"; cp "$latest" /bundle/database.manifest.json; cp "${latest%.manifest.json}.dump" /bundle/database.dump; tar -C /skills -cf /bundle/skills.tar .; sha256sum /bundle/database.dump /bundle/skills.tar > /bundle/files.sha256')
    $configuration = @{ environment = $source.Config.Env; image = $source.Image; candidate = $CandidateImage; createdAt = (Get-Date).ToUniversalTime().ToString('o') } | ConvertTo-Json -Depth 5 -Compress
    $protected = ConvertFrom-SecureString (ConvertTo-SecureString $configuration -AsPlainText -Force)
    $null = Docker @('run', '--rm', '-i', '--network', 'none', '--mount', "type=volume,src=$bundle,dst=/bundle", $pgImage, 'sh', '-ec', 'umask 077; cat > /bundle/configuration.dpapi') $protected
    $saved = Docker @('run', '--rm', '--network', 'none', '--mount', "type=volume,src=$bundle,dst=/bundle,readonly", $pgImage, 'cat', '/bundle/configuration.dpapi')
    $restoredConfiguration = ConvertFrom-Json ([System.Net.NetworkCredential]::new('', (ConvertTo-SecureString $saved)).Password)
    if ($restoredConfiguration.image -ne $source.Image) { throw 'Configuration restore mismatch.' }
    $environment = @{}
    foreach ($entry in $restoredConfiguration.environment) {
        $parts = $entry -split '=', 2
        if ($parts.Length -eq 2) { $environment[$parts[0]] = $parts[1] }
    }
    $manifest = Docker @('run', '--rm', '--network', 'none', '--mount', "type=volume,src=$bundle,dst=/bundle,readonly", $pgImage, 'cat', '/bundle/database.manifest.json') | ConvertFrom-Json
    $null = Docker @('run', '--rm', '--network', 'none', '--mount', "type=volume,src=$bundle,dst=/bundle,readonly", $pgImage, 'sh', '-ec', 'sha256sum -c /bundle/files.sha256 >/dev/null')
    $null = Docker @('network', 'create', '--internal', $restore)
    $networkCreated = $true
    foreach ($suffix in @('pg-data', 'skills')) {
        $name = "$restore-$suffix"
        $null = Docker @('volume', 'create', '--label', 'stackchan.purpose=isolated-restore', $name)
        $createdVolumes.Add($name)
    }
    $null = Docker @('run', '--rm', '--network', 'none', '--mount', "type=volume,src=$bundle,dst=/bundle,readonly", '--mount', "type=volume,src=$restore-skills,dst=/skills", $pgImage, 'sh', '-ec', 'tar -C /skills -xf /bundle/skills.tar; tar -C /skills -df /bundle/skills.tar')
    $createdContainers.Add("$restore-pg")
    $null = Docker @('run', '-d', '--name', "$restore-pg", '--network', $restore, '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', '-e', 'POSTGRES_DB=stackchan', '--mount', "type=volume,src=$restore-pg-data,dst=/var/lib/postgresql", '--mount', "type=volume,src=$bundle,dst=/bundle,readonly", $pgImage)
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        & docker.exe exec "$restore-pg" pg_isready -U postgres -d stackchan *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw 'Isolated PostgreSQL did not become ready.' }
    $null = Docker @('exec', "$restore-pg", 'pg_restore', '-U', 'postgres', '-d', 'stackchan', '--no-owner', '--no-privileges', '--exit-on-error', '/bundle/database.dump')
    $counts = RestoreSql "select json_build_object('persona',(select count(*) from companion_persona_settings),'confirmedMemories',(select count(*) from long_term_memories where confirmation_status='CONFIRMED'),'reminders',(select count(*) from reminders),'expressionPacks',(select count(*) from expression_packs));" | ConvertFrom-Json
    foreach ($property in $manifest.recordCounts.PSObject.Properties) {
        if ($counts.($property.Name) -ne $property.Value) { throw 'Restored record counts differ from backup manifest.' }
    }
    $secretRows = RestoreSql "select api_key_ciphertext || '|' || api_key_iv from llm_provider_settings union all select api_key_ciphertext || '|' || api_key_iv from speech_provider_settings union all select password_ciphertext || '|' || password_iv from icloud_calendar_connections union all select bearer_token_ciphertext || '|' || bearer_token_iv from agent_mcp_connections where bearer_token_ciphertext is not null;"
    $cipher = [System.Security.Cryptography.AesGcm]::new([Convert]::FromBase64String($environment.COMPANION_SECRETS_ENCRYPTION_KEY), 16)
    $decrypted = 0
    try {
        foreach ($row in ($secretRows -split "`n" | Where-Object { $_.Contains('|') })) {
            $parts = $row.Trim() -split '\|', 2
            $bytes = [Convert]::FromBase64String($parts[0])
            $plain = [byte[]]::new($bytes.Length - 16)
            $cipher.Decrypt([Convert]::FromBase64String($parts[1]), $bytes[0..($bytes.Length - 17)], $bytes[($bytes.Length - 16)..($bytes.Length - 1)], $plain)
            [Array]::Clear($plain)
            $decrypted++
        }
    }
    finally { $cipher.Dispose() }
    $identityBefore = RestoreSql "select md5(coalesce(string_agg(id::text || username || password_hash, ',' order by id),'')) from admin_users;"
    # A synthetic administrator is added ONLY to the disposable restored database.
    $testUser = 'restore-check-' + [Guid]::NewGuid().ToString('N')
    $testPassword = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $salt = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
    $derived = [System.Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2($testPassword, $salt, 310000, [System.Security.Cryptography.HashAlgorithmName]::SHA256, 32)
    $testHash = '{pbkdf2@SpringSecurity_v5_8}' + [Convert]::ToHexString([byte[]]($salt + $derived)).ToLowerInvariant()
    $null = RestoreSql "insert into admin_users(id,username,password_hash,created_at) values ('$([Guid]::NewGuid())','$testUser','$testHash',now());"
    $environment.SPRING_DATASOURCE_URL = "jdbc:postgresql://$restore-pg`:5432/stackchan"
    $environment.SPRING_DATASOURCE_USERNAME = 'postgres'
    # Preserve the application's normal bean graph. The internal network and lack
    # of published ports prevent both real devices and external services connecting.
    $environment.COMPANION_BUILD_VERSION = 'companion-v51-restore-check'
    $environment.JAVA_TOOL_OPTIONS = '-Xmx384m'
    $runArguments = @('run', '-d', '--name', "$restore-app", '--network', $restore, '--mount', "type=volume,src=$restore-skills,dst=/app/data/agent-skills")
    foreach ($key in $environment.Keys) { $runArguments += @('-e', "$key=$($environment[$key])") }
    $runArguments += $CandidateImage
    $createdContainers.Add("$restore-app")
    $null = Docker $runArguments
    $url = 'http://127.0.0.1:8080'
    $healthy = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try { $health = Docker @('exec', "$restore-app", 'curl', '-fsS', '--max-time', '2', "$url/api/v1/health") | ConvertFrom-Json; $healthy = $true; break }
        catch { Start-Sleep -Seconds 1 }
    }
    if (!$healthy) { throw 'Isolated application health failed; private logs remain in its container until cleanup.' }
    $version = RestoreSql 'select max(version::int) from flyway_schema_history where success;'
    if ($version.Trim() -ne '51') { throw 'Restored application did not migrate to V51.' }
    $csrf = Docker @('exec', "$restore-app", 'curl', '-fsS', '-c', '/tmp/restore-cookies', "$url/api/v1/auth/csrf") | ConvertFrom-Json
    $loginStatus = Docker @('exec', '-i', "$restore-app", 'curl', '-fsS', '-b', '/tmp/restore-cookies', '-c', '/tmp/restore-cookies', '-H', "$($csrf.headerName): $($csrf.token)", '-H', 'Content-Type: application/json', '--data-binary', '@-', '-o', '/dev/null', '-w', '%{http_code}', "$url/api/v1/auth/login") (@{ username = $testUser; password = $testPassword } | ConvertTo-Json -Compress)
    if ($loginStatus.Trim() -ne '204') { throw 'Restored synthetic administrator login failed.' }
    $null = Docker @('exec', "$restore-app", 'curl', '-fsS', '-b', '/tmp/restore-cookies', "$url/api/v1/memories?limit=1")
    $page = Docker @('exec', "$restore-app", 'curl', '-fsS', '-b', '/tmp/restore-cookies', "$url/")
    if ($page -notmatch '/assets/') { throw 'Restored console shell failed.' }
    $identityAfter = RestoreSql "select md5(coalesce(string_agg(id::text || username || password_hash, ',' order by id),'')) from admin_users where username <> '$testUser';"
    if ($identityBefore -ne $identityAfter) { throw 'Original administrator records changed during restore.' }
    [pscustomobject]@{
        backupVolume = $bundle; backupAt = $manifest.createdAt; databaseSha256 = $manifest.sha256
        oldImage = $source.Image; candidateImage = $CandidateImage; schema = 51
        databaseCountsMatch = $true; skillArchiveRestored = $true; configurationDpapiRoundTrip = $true
        encryptedSettingsVerified = $decrypted; originalAdminRecordsPreserved = $true
        syntheticAdminLogin = $true; consoleShell = $true; outboundNetwork = 'isolated-internal'
    } | ConvertTo-Json
}
finally {
    foreach ($name in $createdContainers) { & docker.exe rm -f $name *> $null }
    foreach ($name in $createdVolumes) { & docker.exe volume rm $name *> $null }
    if ($networkCreated) { & docker.exe network rm $restore *> $null }
    # Keep the release backup bundle, including on failure; never clean a source volume.
    $configuration = $null; $restoredConfiguration = $null; $environment = $null
    $secretRows = $null; $testPassword = $null
}
