param(
    [Parameter(Mandatory = $true)]
    [string]$CustomModelDirectory,
    [string]$DefaultModelDirectory = '',
    [long]$PartitionBytes = 0x100000,
    [long]$MinimumHeadroomBytes = 0x10000
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($DefaultModelDirectory)) {
    $DefaultModelDirectory = Join-Path $PSScriptRoot '..\firmware\managed_components\espressif__esp-sr\model\wakenet_model\wn9l_histackchan_tts3'
}

function Get-ModelDirectories([string]$RootPath, [string]$Description) {
    if (-not (Test-Path -LiteralPath $RootPath -PathType Container)) {
        throw "$Description was not found: $RootPath"
    }
    $root = Get-Item -LiteralPath $RootPath -Force
    if (($root.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Description must not be a symbolic link or junction: $($root.FullName)"
    }

    $directInfo = Join-Path $root.FullName '_MODEL_INFO_'
    if (Test-Path -LiteralPath $directInfo -PathType Leaf) {
        return @($root)
    }

    $directories = @(Get-ChildItem -LiteralPath $root.FullName -Recurse -File -Force -Filter '_MODEL_INFO_' |
        ForEach-Object { $_.Directory } |
        Sort-Object -Property FullName -Unique)
    if ($directories.Count -eq 0) {
        throw "$Description contains no WakeNet model directory with _MODEL_INFO_: $($root.FullName)"
    }
    return $directories
}

function Test-ModelDirectory([IO.DirectoryInfo]$Directory, [string]$Description) {
    if (($Directory.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Description model directory must not be a symbolic link or junction: $($Directory.FullName)"
    }
    if ($Directory.Name.Length -gt 32 -or $Directory.Name -notmatch '^wn[0-9][a-z0-9_]*$') {
        throw "$Description model name must be a lowercase WakeNet identifier of at most 32 characters: $($Directory.Name)"
    }

    $nestedDirectories = @(Get-ChildItem -LiteralPath $Directory.FullName -Directory -Force)
    if ($nestedDirectories.Count -ne 0) {
        throw "$Description model directory must not contain nested directories: $($Directory.FullName)"
    }
    $files = @(Get-ChildItem -LiteralPath $Directory.FullName -File -Force)
    if ($files.Count -ne 3) {
        throw "$Description model must contain exactly _MODEL_INFO_, one *_data file, and one *_index file: $($Directory.FullName)"
    }
    foreach ($file in $files) {
        if (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "$Description model files must not be symbolic links: $($file.FullName)"
        }
        if ($file.Name.Length -gt 32 -or $file.Name -notmatch '^(?:_MODEL_INFO_|[a-z0-9_]+_(?:data|index))$') {
            throw "$Description model contains an unsupported file name: $($file.Name)"
        }
        if ($file.Length -le 0) {
            throw "$Description model contains an empty file: $($file.FullName)"
        }
    }
    if (@($files | Where-Object Name -EQ '_MODEL_INFO_').Count -ne 1 -or
        @($files | Where-Object Name -Like '*_data').Count -ne 1 -or
        @($files | Where-Object Name -Like '*_index').Count -ne 1) {
        throw "$Description model file set is incomplete or ambiguous: $($Directory.FullName)"
    }

    $infoFile = @($files | Where-Object Name -EQ '_MODEL_INFO_')[0]
    $infoPath = $infoFile.FullName
    if ($infoFile.Length -gt 4096) {
        throw "$Description _MODEL_INFO_ exceeds the 4096-byte limit: $infoPath"
    }
    $infoBytes = [IO.File]::ReadAllBytes($infoPath)
    if ($infoBytes.Contains([byte]0)) {
        throw "$Description _MODEL_INFO_ must not contain NUL bytes: $infoPath"
    }
    try {
        $info = [Text.UTF8Encoding]::new($false, $true).GetString($infoBytes).Trim()
    }
    catch {
        throw "$Description _MODEL_INFO_ is not valid UTF-8: $infoPath"
    }
    $metadataLines = @($info -split '\r?\n' |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and -not $_.StartsWith('#') })
    $metadataPattern = '^wakenet[a-z0-9]+_[a-z0-9]+(?:_[^_\r\n]+_[1-9][0-9]*_(?:0(?:\.[0-9]+)?|1(?:\.0+)?)_(?:0(?:\.[0-9]+)?|1(?:\.0+)?))+$'
    if ($metadataLines.Count -ne 1 -or $metadataLines[0] -notmatch $metadataPattern) {
        throw "$Description _MODEL_INFO_ does not contain one valid WakeNet metadata record: $infoPath"
    }

    $dataBytes = [long](($files | Measure-Object -Property Length -Sum).Sum)
    return [pscustomobject]@{
        Name = $Directory.Name
        FileCount = $files.Count
        DataBytes = $dataBytes
    }
}

if ($PartitionBytes -le 0 -or $MinimumHeadroomBytes -lt 0 -or $MinimumHeadroomBytes -ge $PartitionBytes) {
    throw 'PartitionBytes and MinimumHeadroomBytes do not define a usable model partition budget'
}

$defaultDirectories = @(Get-ModelDirectories $DefaultModelDirectory 'Default model directory')
if ($defaultDirectories.Count -ne 1) {
    throw "Default model directory must resolve to exactly one model: $DefaultModelDirectory"
}
$customDirectories = @(Get-ModelDirectories $CustomModelDirectory 'Custom model directory')
$models = @()
$models += Test-ModelDirectory $defaultDirectories[0] 'Default'
foreach ($directory in $customDirectories) {
    $models += Test-ModelDirectory $directory 'Custom'
}

$duplicates = @($models | Group-Object -Property Name | Where-Object Count -GT 1)
if ($duplicates.Count -ne 0) {
    throw "Custom model names must not replace an existing packaged model: $($duplicates[0].Name)"
}

# ESP-SR pack_model.py stores a 4-byte count, a 36-byte model header,
# a 40-byte header per file, and then each file's bytes without padding.
$fileCount = [long](($models | Measure-Object -Property FileCount -Sum).Sum)
$dataBytes = [long](($models | Measure-Object -Property DataBytes -Sum).Sum)
$packedBytes = 4L + 36L * $models.Count + 40L * $fileCount + $dataBytes
$headroomBytes = $PartitionBytes - $packedBytes
if ($headroomBytes -lt $MinimumHeadroomBytes) {
    throw "Wake-word models exceed the safe partition budget: packed=$packedBytes bytes, partition=$PartitionBytes bytes, headroom=$headroomBytes bytes, requiredHeadroom=$MinimumHeadroomBytes bytes"
}

$modelNames = ($models | ForEach-Object Name) -join ','
Write-Output "Wake-word model package passed: models=$modelNames, packed=$packedBytes bytes, partition=$PartitionBytes bytes, headroom=$headroomBytes bytes"
