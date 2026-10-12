param(
    [string]$ImagePrefix = 'stackchan-foundation-server:device008',
    [string]$WebDependencyImage = '',
    [string]$WebDependencySourceCommit = ''
)

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
    $dockerfile = Join-Path $source 'server\Dockerfile'
    $dependencyImageId = $null
    if ($WebDependencyImage) {
        if ($WebDependencyImage -notmatch '^[a-z0-9./:_-]+$' -or $WebDependencySourceCommit -notmatch '^[0-9a-f]{40}$') {
            throw 'Cached dependencies require a local image and exact source commit'
        }
        & git diff --quiet $WebDependencySourceCommit $commit -- package.json pnpm-lock.yaml pnpm-workspace.yaml packages apps/stackchan-console/package.json
        if ($LASTEXITCODE -ne 0) { throw 'Cached dependency source does not match the frozen dependency tree' }
        $dependencyImageId = (& docker image inspect --format '{{.Id}}' $WebDependencyImage).Trim()
        if ($LASTEXITCODE -ne 0 -or $dependencyImageId -notmatch '^sha256:[0-9a-f]{64}$') { throw 'Cannot fingerprint dependency image' }
        foreach ($file in @('package.json', 'pnpm-lock.yaml', 'pnpm-workspace.yaml', 'apps/stackchan-console/package.json')) {
            $actual = & docker run --rm --network none --entrypoint sha256sum $dependencyImageId "/workspace/$file"
            if ($LASTEXITCODE -ne 0 -or ($actual -split '\s+')[0] -ine (Get-FileHash -LiteralPath (Join-Path $source $file) -Algorithm SHA256).Hash) {
                throw 'Cached dependency image content differs from frozen source'
            }
        }
        $pnpmVersion = & docker run --rm --network none --entrypoint pnpm $dependencyImageId --version
        if ($LASTEXITCODE -ne 0 -or $pnpmVersion.Trim() -ne '11.13.1') { throw 'Cached dependency toolchain version differs' }
        $originalDockerfile = Get-Content -LiteralPath $dockerfile -Raw
        $serverStage = $originalDockerfile.IndexOf('FROM maven:')
        if ($serverStage -lt 0) { throw 'Cannot locate the unchanged server build stage' }
        $webStage = @"
FROM $WebDependencyImage AS web-build
WORKDIR /workspace
RUN rm -rf /workspace/apps/stackchan-console/src /workspace/apps/stackchan-console/dist
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.json uno.config.ts ./
COPY packages ./packages
COPY apps/stackchan-console ./apps/stackchan-console
COPY scripts ./scripts
RUN pnpm --filter @stackchan/console run build

"@
        $dockerfile = Join-Path $directory 'Dockerfile.cached-dependencies'
        [IO.File]::WriteAllText($dockerfile, $webStage + $originalDockerfile.Substring($serverStage), [Text.UTF8Encoding]::new($false))
    } elseif ($WebDependencySourceCommit) { throw 'Dependency source requires a dependency image' }
    & docker build --label "org.opencontainers.image.revision=$commit" -f $dockerfile -t $image $source
    if ($LASTEXITCODE -ne 0) { throw 'Candidate image build failed' }
    if ($dependencyImageId) {
        $afterDependencyImageId = (& docker image inspect --format '{{.Id}}' $WebDependencyImage).Trim()
        if ($LASTEXITCODE -ne 0 -or $afterDependencyImageId -ne $dependencyImageId) { throw 'Dependency image changed during build' }
    }
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
        buildDockerfileSha256 = (Get-FileHash -LiteralPath $dockerfile -Algorithm SHA256).Hash
        webDependencyImageId = $dependencyImageId
        webDependencySourceCommit = $WebDependencySourceCommit
        createdAt = (Get-Date).ToUniversalTime().ToString('o')
    }
    $manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'manifest.json')
    Write-Output "Candidate manifest: $directory\manifest.json"
} finally { Pop-Location }
