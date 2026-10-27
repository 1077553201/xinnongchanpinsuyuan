[CmdletBinding()]
param([string]$Tag = 'v1.0')
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($Tag -notmatch '^v[0-9]+\.[0-9]+(?:\.[0-9]+)?$') { throw 'Invalid release tag.' }
Push-Location $repositoryRoot
try {
    $commit = & git rev-parse --verify "$Tag^{commit}"
    if ($LASTEXITCODE -ne 0) { throw 'Create the release tag before packaging.' }
    $headCommit = & git rev-parse HEAD
    if ($headCommit -ne $commit) { throw 'Package from the tagged checkout.' }
    & git diff --quiet HEAD --
    if ($LASTEXITCODE -ne 0) { throw 'Commit tracked changes before packaging.' }
    $version = (Get-Content VERSION -Raw).Trim()
    $jar = Join-Path $repositoryRoot "backend/target/agri-trace-backend-$version.jar"
    $frontend = Join-Path $repositoryRoot 'frontend/dist'
    if (-not (Test-Path -LiteralPath $jar) -or -not (Test-Path -LiteralPath (Join-Path $frontend 'index.html'))) { throw 'Build the backend and frontend before packaging.' }
    $outputRoot = Join-Path $repositoryRoot "releases/$Tag"
    if (Test-Path -LiteralPath $outputRoot) { throw 'Release output already exists; use a fresh output directory.' }
    $packageName = "agri-trace-$Tag"
    $packageRoot = Join-Path $outputRoot $packageName
    New-Item -ItemType Directory -Path $packageRoot, (Join-Path $packageRoot 'backend'), (Join-Path $packageRoot 'frontend'), (Join-Path $packageRoot 'logs') -Force | Out-Null
    Copy-Item -LiteralPath $jar -Destination (Join-Path $packageRoot 'backend')
    Copy-Item -LiteralPath $frontend -Destination (Join-Path $packageRoot 'frontend') -Recurse
    # Copy only tracked documentation/configuration; ignored local material never enters the bundle.
    $trackedFiles = & git ls-files docs database deploy/emqx .env.example VERSION
    if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate release files.' }
    foreach ($relativePath in $trackedFiles) {
        $destination = Join-Path $packageRoot $relativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $repositoryRoot $relativePath) -Destination $destination
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Start-Release.ps1') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'deploy/nginx.release.conf') -Destination (Join-Path $packageRoot 'nginx.conf')
    "# AgriTrace $Tag`n`nStart with docs/RELEASE_v1.0.md. Configure your own .env and external services; no private configuration is included.`n" | Set-Content -LiteralPath (Join-Path $packageRoot 'README.md') -Encoding UTF8
    @{ version = $version; tag = $Tag; commit = $commit.Trim(); packagedAtUtc = [DateTime]::UtcNow.ToString('o'); kind = 'trial' } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $packageRoot 'RELEASE.json') -Encoding UTF8
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jarArchive = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $privateEntries = @($jarArchive.Entries | Where-Object { $_.FullName -match '^BOOT-INF/classes/(?:.*\.(?:key|pem|crt|p12|keystore)$|application-local\.properties$)' })
        if ($privateEntries.Count) { throw 'Private configuration or certificate material detected in application JAR.' }
    } finally { $jarArchive.Dispose() }
    $sourceZip = Join-Path $outputRoot "$packageName-source.zip"
    & git archive --format=zip "--prefix=$packageName-source/" "--output=$sourceZip" $Tag
    if ($LASTEXITCODE -ne 0) { throw 'Source archive failed.' }
    $runtimeZip = Join-Path $outputRoot "$packageName.zip"
    [IO.Compression.ZipFile]::CreateFromDirectory($packageRoot, $runtimeZip, [IO.Compression.CompressionLevel]::Optimal, $true)
    $checksums = foreach ($file in @($runtimeZip, $sourceZip)) { "$( (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant())  $([IO.Path]::GetFileName($file))" }
    $checksums | Set-Content -LiteralPath (Join-Path $outputRoot 'SHA256SUMS.txt') -Encoding ASCII
    Write-Output "Release package created: $outputRoot"
} finally { Pop-Location }
