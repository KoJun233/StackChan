param([string]$Tests = '')

$ErrorActionPreference = 'Stop'
if ($Tests -and $Tests -notmatch '^[A-Za-z0-9_,]+$') { throw 'Invalid test class list' }
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$name = "stackchan-companion-tests-$([Guid]::NewGuid().ToString('N'))"
$report = Join-Path $root ".tools\$name"
New-Item -ItemType Directory -Path $report -Force | Out-Null
$testOption = if ($Tests) { "-Dtest=$Tests" } else { '' }
$command = @"
set -eu
mkdir -p /work /maven-repository
cp -a /dependency-seed/. /maven-repository/
cp -a /source/pom.xml /source/src /source/wakenet-models /work/
cd /work
# The read-only seed includes artifacts acquired under both central and aliyun.
# Declare the seed provenance IDs for Maven's enhanced local repository; -o forbids network resolution.
cat > seed-settings.xml <<'SETTINGS'
<settings><profiles><profile><id>seed</id><repositories>
<repository><id>aliyun</id><url>https://maven.aliyun.com/repository/public</url></repository>
</repositories><pluginRepositories>
<pluginRepository><id>aliyun</id><url>https://maven.aliyun.com/repository/public</url></pluginRepository>
</pluginRepositories></profile></profiles><activeProfiles><activeProfile>seed</activeProfile></activeProfiles></settings>
SETTINGS
set +e
mvn -o -s seed-settings.xml -B -ntp -Dmaven.repo.local=/maven-repository '-DargLine=-Xmx768m -XX:ActiveProcessorCount=2' $testOption test
result=`$?
exit `$result
"@
$image = 'maven@sha256:2b4496088e7b80ae10a8c9f74e574ea21380325a006ec684532ad6bad5bc7273'
& docker run --detach --name $name --cpus 2 --memory 2g `
    --mount "type=bind,source=$root\server,target=/source,readonly" `
    --mount "type=bind,source=$env:USERPROFILE\.m2\repository,target=/dependency-seed,readonly" `
    --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' `
    --env TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --env MAVEN_OPTS=-Xmx640m `
    $image sh -c $command | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated test container' }
try {
    $result = & docker wait $name
    if ($LASTEXITCODE -ne 0) { throw 'Could not wait for test container' }
    $state = (& docker inspect --format '{{json .State}}' $name | ConvertFrom-Json)
    $state | ConvertTo-Json | Set-Content (Join-Path $report 'container-state.json')
    & docker logs $name 2>&1 | Set-Content (Join-Path $report 'test.log')
    Get-Content (Join-Path $report 'test.log') | Select-String 'Tests run:|BUILD SUCCESS|BUILD FAILURE|\[ERROR\]' |
        Select-Object -Last 70 | ForEach-Object { Write-Output $_.Line }
    & docker cp "${name}:/work/target/surefire-reports" $report
    if ($LASTEXITCODE -ne 0) {
        Get-Content (Join-Path $report 'test.log') -Tail 25
        throw "Could not preserve test reports; log and state: $report"
    }
    Write-Output "Reports: $report"
    if ([int]$result -ne 0 -or $state.OOMKilled) { throw "Linux test run failed: exit=$result OOM=$($state.OOMKilled)" }
} finally {
    & docker rm --force $name | Out-Null
}
