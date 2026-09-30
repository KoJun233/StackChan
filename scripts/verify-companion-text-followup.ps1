param()

# Uses only invented facts. Creates one temporary web conversation and administrator,
# then removes both; never prints credentials or existing conversation content.
$ErrorActionPreference = 'Stop'
$baseUrl = 'http://127.0.0.1:8080'
$databaseContainer = 'stackchan-foundation-postgres-1'
$adminId = [guid]::NewGuid().ToString()
$adminName = 'companion-text-once-' + [guid]::NewGuid().ToString('N')
$adminPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$salt = [Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
$derived = [Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2(
    $adminPassword, $salt, 310000, [Security.Cryptography.HashAlgorithmName]::SHA256, 32
)
$adminHash = '{pbkdf2@SpringSecurity_v5_8}' + [Convert]::ToHexString([byte[]]($salt + $derived)).ToLowerInvariant()
$session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$adminCreated = $false
$conversationId = $null
$headers = @{}
$assistantIds = [System.Collections.Generic.HashSet[string]]::new()
$stage = 'start'
$primaryFailure = $null

function QueryDatabase([string]$Sql) {
    $result = $Sql | & docker.exe exec -i $databaseContainer psql -XAt -v ON_ERROR_STOP=1 -U stackchan -d stackchan 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Database query failed at $stage; private output suppressed."
    }
    return @($result)
}

function CaptureAssistantIds {
    if (!$conversationId) { return }
    $messages = Invoke-RestMethod "$baseUrl/api/v1/conversations/$conversationId/messages" -WebSession $session
    foreach ($message in $messages) {
        $parsedId = [guid]::Empty
        if ($message.role -eq 'ASSISTANT' -and [guid]::TryParse([string]$message.id, [ref]$parsedId)) {
            $null = $assistantIds.Add($parsedId.ToString())
        }
        Write-Output $message
    }
}

try {
    $stage = 'insert-administrator'
    $null = QueryDatabase "insert into admin_users(id, username, password_hash, created_at) values ('$adminId', '$adminName', '$adminHash', now());"
    $adminCreated = $true
    $stage = 'login'
    $csrf = Invoke-RestMethod "$baseUrl/api/v1/auth/csrf" -WebSession $session
    $headers = @{ $csrf.headerName = $csrf.token }
    $loginBody = @{ username = $adminName; password = $adminPassword } | ConvertTo-Json -Compress
    $null = Invoke-RestMethod "$baseUrl/api/v1/auth/login" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body $loginBody
    $csrf = Invoke-RestMethod "$baseUrl/api/v1/auth/csrf" -WebSession $session
    $headers = @{ $csrf.headerName = $csrf.token }

    $stage = 'create-conversation'
    $conversation = Invoke-RestMethod "$baseUrl/api/v1/conversations" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body '{}'
    $conversationId = [string]$conversation.id
    if (!$conversationId) { throw 'Temporary conversation creation returned no ID.' }

    $prompts = @(
        '这是一次虚构对话测试，不是我的真实经历或偏好。假设蓝色纸飞机的代号是“星星”，红色纸飞机的代号是“火苗”。请只回答“收到”。',
        '刚才蓝色纸飞机的代号是什么？请只写代号。'
    )
    foreach ($prompt in $prompts) {
        $stage = 'send-turn'
        $body = @{ clientMessageId = [guid]::NewGuid().ToString(); content = $prompt } | ConvertTo-Json -Compress
        $response = Invoke-WebRequest "$baseUrl/api/v1/conversations/$conversationId/messages`:stream" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 180
        if ($response.StatusCode -ne 200 -or $response.Content -notmatch 'event:\s*completed') {
            throw 'A temporary text turn did not complete.'
        }
    }
    $stage = 'read-answer'
    $messages = @(CaptureAssistantIds)
    Write-Output "Synthetic message states: $(@($messages | ForEach-Object { "$($_.role):$($_.generationStatus)" }) -join ',')"
    $answers = @($messages | Where-Object { $_.role -eq 'ASSISTANT' -and $_.generationStatus -eq 'COMPLETED' })
    if ($answers.Count -ne 2) { throw "Expected two completed assistant turns; got $($answers.Count)." }
    $answer = [string]$answers[-1].content
    Write-Output "Synthetic two-turn answer: $($answer.Substring(0, [Math]::Min(160, $answer.Length)))"
    if (!$answer.Contains('星星') -or $answer.Contains('火苗')) {
        throw 'The second reply did not unambiguously recall the blue paper plane.'
    }
    Write-Output 'Synthetic web text follow-up passed.'
}
catch {
    $primaryFailure = $_
    Write-Warning "Synthetic text test failed at $stage."
}
finally {
    try {
        $stage = 'cleanup-conversation'
        if ($conversationId) {
            try { $null = CaptureAssistantIds } catch { Write-Warning 'Could not inspect temporary assistant IDs before cleanup.' }
            $null = Invoke-RestMethod "$baseUrl/api/v1/personal-data/conversations/$conversationId" -Method Delete -WebSession $session -Headers $headers
        }
        $stage = 'cleanup-suggestions'
        if ($assistantIds.Count -gt 0) {
            # Suggestion extraction is asynchronous. Only inspect IDs from this test, never other memories.
            Start-Sleep -Seconds 5
            $quoted = @($assistantIds | ForEach-Object { "'$_'" }) -join ','
            $suggestionIds = @(QueryDatabase "select id::text from long_term_memories where source = 'ASSISTANT_SUGGESTED' and confirmation_status = 'PENDING' and source_turn_id in ($quoted);")
            foreach ($suggestionId in $suggestionIds) {
                if ($suggestionId) {
                    $null = Invoke-RestMethod "$baseUrl/api/v1/memories/$suggestionId" -Method Delete -WebSession $session -Headers $headers
                }
            }
            if ($suggestionIds.Count -gt 0) { Write-Output "Removed $($suggestionIds.Count) temporary memory suggestion(s)." }
        }
    }
    finally {
        try {
            if ($adminCreated) { $null = Invoke-RestMethod "$baseUrl/api/v1/auth/logout" -Method Post -WebSession $session -Headers $headers }
        } catch { Write-Warning 'Temporary session logout could not be confirmed.' }
        if ($adminCreated) {
            $stage = 'cleanup-administrator'
            $null = QueryDatabase "delete from admin_users where id = '$adminId' and username = '$adminName';"
            if (@(QueryDatabase "select id from admin_users where id = '$adminId';").Count -ne 0) {
                throw 'Temporary administrator cleanup failed.'
            }
        }
    }
    $adminPassword = $null
    $adminHash = $null
    $loginBody = $null
}
if ($primaryFailure) { throw $primaryFailure }
