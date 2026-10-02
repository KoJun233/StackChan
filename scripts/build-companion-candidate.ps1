param([string]$ImagePrefix = 'stackchan-foundation-server:device008')

$ErrorActionPreference = 'Stop'
if ($ImagePrefix -notmatch '^[a-z0-9./:_-]+$') { throw 'Invalid local image prefix' }
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $root
try {
    $commit = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}$') { throw 'Cannot resolve source commit' }
    & git diff --quiet HEAD -- server apps packages scripts package.json pnpm-lock.yaml pnpm-workspace.yaml tsconfig.json uno.config.ts firmware
    if ($LASTEXITCODE -ne 0) { throw 'Candidate source must match HEAD' }
    $untracked = & git ls-files --others --exclude-standard -- server apps packages scripts firmware
    if ($LASTEXITCODE -ne 0 -or ($untracked | Where-Object { $_ -match '\.(java|sql|vue|ts|json|ya?ml|c|cpp|h|ps1|py|mjs)$|(?:CMakeLists\.txt|Dockerfile)$' })) {
        throw 'Candidate source contains untracked code or configuration'
    }
    $directory = Join-Path $root ".tools\device008-$($commit.Substring(0, 12))-$([Guid]::NewGuid().ToString('N'))"
    $source = Join-Path $directory 'source'
    New-Item -ItemType Directory -Path $source -Force | Out-Null
    $archive = Join-Path $directory 'source.tar'
    & git archive --format=tar -o $archive $commit
    if ($LASTEXITCODE -ne 0) { throw 'Cannot archive committed source' }
    & tar.exe -xf $archive -C $source
    if ($LASTEXITCODE -ne 0) { throw 'Cannot extract committed source' }
    $image = "$ImagePrefix-$($commit.Substring(0, 12))"
    & docker build --label "org.opencontainers.image.revision=$commit" -f (Join-Path $source 'server\Dockerfile') -t $image $source
    if ($LASTEXITCODE -ne 0) { throw 'Candidate image build failed' }
    $imageId = (& docker image inspect --format '{{.Id}}' $image).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot fingerprint candidate image' }
    $name = "stackchan-candidate-extract-$([Guid]::NewGuid().ToString('N'))"
    & docker create --name $name --network none $image | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create stopped artifact extraction container' }
    try {
        & docker cp "${name}:/app/app.jar" (Join-Path $directory 'app.jar')
        if ($LASTEXITCODE -ne 0) { throw 'Cannot preserve candidate JAR' }
        & docker cp "${name}:/app/public" (Join-Path $directory 'public')
        if ($LASTEXITCODE -ne 0) { throw 'Cannot preserve candidate console' }
    } finally { & docker rm $name | Out-Null }
    $schemaVersions = Get-ChildItem -LiteralPath (Join-Path $source 'server\src\main\resources\db\migration') -File |
        ForEach-Object { if ($_.Name -match '^V(\d+)__.*\.sql$') { [int]$Matches[1] } }
    $expectedSchema = ($schemaVersions | Measure-Object -Maximum).Maximum
    if ($null -eq $expectedSchema) { throw 'Cannot resolve archived migration version' }
    $manifest = [ordered]@{
        sourceCommit = $commit
        sourceArchiveSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
        image = $image
        imageId = $imageId
        jarSha256 = (Get-FileHash -LiteralPath (Join-Path $directory 'app.jar') -Algorithm SHA256).Hash
        consoleIndexSha256 = (Get-FileHash -LiteralPath (Join-Path $directory 'public\index.html') -Algorithm SHA256).Hash
        expectedSchema = [int]$expectedSchema
        createdAt = (Get-Date).ToUniversalTime().ToString('o')
    }
    $manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'manifest.json')
    Write-Output "Candidate manifest: $directory\manifest.json"
} finally { Pop-Location }
